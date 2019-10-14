package scalang

import org.specs2.mutable._

class ServiceSpec extends SpecificationWithJUnit with InEpmd {
  sequential
  
  trait Context extends After {
    val cookie = "test"
    var node : ErlangNode = null

    def after() {
      node.shutdown
    }
  }
  
  "Service" should {
    "deliver casts" in new Context {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CastNoopService,NoArgs](NoArgs)
      node.send(service, (Symbol("$gen_cast"),'blah))
      node.isAlive(service) must be_==(true)
    }

    "deliver calls" in new Context {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CallEchoService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, (Symbol("$gen_call"), (mbox.self, ref), 'blah))
      mbox.receive must be_==((ref,'blah))
    }

    "respond to pings" in new Context {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CastNoopService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      val ref = node.makeRef
      node.send(service, ('ping, mbox.self, ref))
      mbox.receive must be_==(('pong, ref))
    }

    "call and response" in new Context {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[CallAndReceiveService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      node.send(service, mbox.self)
      val (Symbol("$gen_call"), (_, ref : Reference), req) = mbox.receive
      req must be_==("blah")
      node.send(service, (ref, "barf"))
      mbox.receive must be_==("barf")
    }
    
    "trap exits" in new Context {
      node = Node(Symbol("test@localhost"), cookie)
      val service = node.spawnService[TrapExitService,NoArgs](NoArgs)
      val mbox = node.spawnMbox
      mbox.link(service)
      mbox.exit('terminate)
      Thread.sleep(1000)
      node.isAlive(service) must be_==(true)
    }
  }
}

class TrapExitService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {
  
  override def trapExit(from : Pid, reason : Any) {
    println("herp " + reason)
  }
  
}

class CallAndReceiveService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {

  override def handleCast(msg : Any) {
    throw new Exception
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    throw new Exception
  }

  override def handleInfo(msg : Any) {
    val pid = msg.asInstanceOf[Pid]
    val response = call(pid, "blah")
    pid ! response
  }
}

class CastNoopService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {
  override def handleCast(msg : Any) {
    println("cast received " + msg)
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    throw new Exception
  }

  override def handleInfo(msg : Any) {
    throw new Exception
  }
}

class CallEchoService(ctx : ServiceContext[NoArgs]) extends Service(ctx) {

  override def handleCast(msg : Any) {
    throw new Exception
  }

  override def handleCall(tag : (Pid, Reference), msg : Any) : Any = {
    msg
  }

  override def handleInfo(msg : Any) {
    throw new Exception
  }
}
