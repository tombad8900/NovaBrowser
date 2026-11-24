package app.nova.browser
import org.junit.Assert.*
import org.junit.Test
class WrappedHintTest {
 @Test fun decodesOnlyExplicitKnownMediaHints(){
  val signed="https://cdn.test/movie.m3u8?token=a+b%2Fc&x=1"
  val wrapper="https://site.test/player?url="+java.net.URLEncoder.encode(signed,"UTF-8")
  val found=Sniffer.foundFromUrls(listOf(wrapper),"page").single()
  assertEquals(signed,found.url);assertEquals("HLS",found.kind);assertEquals("wrapped-hint",found.source)
 }
 @Test fun base64AndNestedAreBoundedHints(){
  val media="https://cdn.test/sub.ssa"
  val encoded=java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(media.toByteArray())
  assertEquals(media,Sniffer.unwrapMediaHint("https://site.test/player?src=$encoded"))
  val inner="https://site.test/inner?src="+java.net.URLEncoder.encode(media,"UTF-8")
  assertEquals(media,Sniffer.unwrapMediaHint("https://site.test/outer?url="+java.net.URLEncoder.encode(inner,"UTF-8")))
  assertNull(Sniffer.unwrapMediaHint("https://site.test/player?other=$encoded"))
  assertNull(Sniffer.unwrapMediaHint("https://site.test/player?url=javascript%3Aalert(1)"))
  assertNull(Sniffer.unwrapMediaHint("https://site.test/player?url=https%3A%2F%2Fuser%3Apass%40cdn.test%2Fa.mp4"))
 }
 @Test fun arbitraryKeywordsAndLongInputNotSources(){
  assertTrue(Sniffer.foundFromUrls(listOf("https://site.test/player?id=4","https://site.test/captions?lang=ar","https://site.test/?url=https://site.test/home"),"page").isEmpty())
  assertNull(Sniffer.unwrapMediaHint("x".repeat(16385)))
 }
}
