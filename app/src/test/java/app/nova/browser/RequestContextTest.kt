package app.nova.browser
import org.junit.Test
import org.junit.Assert.*
class RequestContextTest {
 @Test fun credentialsStayScoped() {
  val c=RequestContext("https://cdn.example/a/master.m3u8", mapOf("Cookie" to "session=private","Host" to "bad","X-Evil" to "bad"),1000)
  assertEquals("session=private",c.safeHeaders("https://cdn.example/a/segment.ts",2000)["Cookie"])
  assertTrue(c.safeHeaders("https://evil.example/a/segment.ts",2000).isEmpty())
  assertTrue(c.safeHeaders("http://cdn.example/a/segment.ts",2000).isEmpty())
  assertTrue(c.safeHeaders("https://cdn.example/other/file",2000).isEmpty())
  assertTrue(c.safeHeaders("https://cdn.example/a/segment.ts",2000000).isEmpty())
  assertFalse(c.safeHeaders("https://cdn.example/a/segment.ts",2000).containsKey("Host"))
 }
 @Test fun pathsCannotEscapeScope() {
  val c=RequestContext("https://cdn.example/a/master.m3u8",mapOf("Cookie" to "x=y"))
  assertTrue(c.safeHeaders("https://cdn.example/a/../secret").isEmpty())
  assertTrue(c.safeHeaders("https://cdn.example/a/%2e%2e/secret").isEmpty())
 }
 @Test fun preserveSignedQuery() { assertNotEquals(sourceKey("https://a/v?token=1"),sourceKey("https://a/v?token=2"));assertEquals(sourceKey("https://a/v#one"),sourceKey("https://a/v#two")) }
 @Test fun hlsMimeAliases() {assertEquals("HLS",Sniffer.classifyMime("https://a/stream","audio/x-mpegurl"))}
 @Test fun htmlIsNotMedia() { assertNull(Sniffer.classifyMime("https://a/master.m3u8","text/html"));assertEquals("HLS",Sniffer.classifyMime("https://a/noextension","application/vnd.apple.mpegurl")) }
}
