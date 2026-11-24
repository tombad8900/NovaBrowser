package app.nova.browser

import java.net.ServerSocket
import java.util.concurrent.Executors
import java.net.InetSocketAddress
import okhttp3.Request
import org.junit.Assert.*
import org.junit.Test

class ScopedMediaHttpTest {
 @Test fun headersFollowExactResourcesNotRedirectsOrOtherSources() {
  val seen=java.util.concurrent.ConcurrentHashMap<String,String>()
  val a=ServerSocket(0);val b=ServerSocket(0)
  val origin="http://127.0.0.1:${a.localPort}"
  val other="http://127.0.0.1:${b.localPort}"
  val pool=Executors.newCachedThreadPool()
  fun serve(server:ServerSocket,redirectTarget:Boolean) {pool.execute {
   try{while(!server.isClosed){val socket=server.accept();pool.execute {
    socket.use{sock->
     val input=sock.getInputStream().bufferedReader();val line=input.readLine().orEmpty();val path=line.split(" ").getOrNull(1).orEmpty()
     val headers=mutableMapOf<String,String>();while(true){val h=input.readLine()?:break;if(h.isEmpty())break;headers[h.substringBefore(':').lowercase()]=h.substringAfter(':').trim()}
     seen[if(redirectTarget)"redirected" else path]=headers["cookie"]?:"none"
     val response=if(path.endsWith("redirect"))"HTTP/1.1 302 Found\r\nLocation: $other/video.mp4\r\nContent-Length: 0\r\nConnection: close\r\n\r\n"
     else "HTTP/1.1 200 OK\r\nContent-Type: ${if(path.endsWith("fake.m3u8"))"text/html" else "video/mp4"}\r\nContent-Length: 2\r\nConnection: close\r\n\r\nok"
     sock.getOutputStream().write(response.toByteArray())
    }
   }}}catch(_:java.net.SocketException){}
  }}
  serve(a,false);serve(b,true)
  try {
   val first=RequestContext("$origin/media/first.mp4",mapOf("Cookie" to "first=1"))
   val second=RequestContext("$origin/media/second.mp4",mapOf("Cookie" to "second=1"))
   val c=ScopedMediaHttp.client(listOf(first,second),first)
   fun get(path:String){c.newCall(Request.Builder().url(origin+path).build()).execute().use{assertTrue(it.isSuccessful)}}
   get("/media/first.mp4");get("/media/second.mp4");get("/media/segment.m4s");get("/media/redirect")
   assertEquals("first=1",seen["/media/first.mp4"]);assertEquals("second=1",seen["/media/second.mp4"])
   assertEquals("first=1",seen["/media/segment.m4s"]);assertEquals("none",seen["redirected"])
   try{get("/media/fake.m3u8");fail("HTML must fail")}catch(expected:java.io.IOException){}
  }finally{a.close();b.close();pool.shutdownNow()}
 }
}
