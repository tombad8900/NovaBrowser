package app.nova.browser
import org.junit.Assert.*
import org.junit.Test
class SubtitleCandidateTest {
 @Test fun extensionlessTrackCreatesCandidateNotVerifiedSource(){
  val s=subtitleFromTrack("https://cdn.test/caption?id=ar","ar","Arabic",null,null)
  assertEquals("SUB",s.kind);assertEquals("text/vtt",s.mime);assertEquals("track-hint",s.evidence);assertTrue(s.context.headers.isEmpty())
 }
 @Test fun mergePreservesObservedContextAndFormat(){
  val c=RequestContext("https://cdn.test/caption?id=ar",mapOf("Cookie" to "one"))
  val previous=PlaybackSource(url=c.url,kind="SUB",mime="application/ttml+xml",context=c,evidence="network")
  val s=subtitleFromTrack(c.url,"ar","Arabic",previous,null)
  assertEquals(c,s.context);assertEquals("application/ttml+xml",s.mime);assertEquals("network",s.evidence)
 }
 @Test fun provenanceIsNotPlaybackProof(){
  val hint=subtitleFromTrack("https://cdn.test/sub","ar","Arabic",null,null)
  assertFalse(hint.networkObserved);assertFalse(hint.hasRequestHeaders)
  val context=RequestContext(hint.url,mapOf("Referer" to "https://site.test/"))
  val seen=subtitleFromTrack(hint.url,"ar","Arabic",null,context)
  assertTrue(seen.networkObserved);assertTrue(seen.hasRequestHeaders)
 }
 @Test fun subtitleMimeFormats(){
  for(m in listOf("application/ttml+xml","text/x-ssa","text/x-ass","application/x-ssa","application/x-ass"))assertEquals("SUB",Sniffer.classifyMime("https://cdn.test/caption",m))
  assertEquals("text/x-ssa",Sniffer.mimeForSub("https://cdn.test/sub","application/x-ass"))
  assertEquals("application/ttml+xml",Sniffer.mimeForSub("https://cdn.test/sub","application/ttml+xml"))
  assertEquals("text/x-ssa",Sniffer.mimeForSub("https://cdn.test/sub.ssa"))
  assertEquals("application/ttml+xml",Sniffer.mimeForSub("https://cdn.test/sub.dfxp"))
  assertNull(Sniffer.classifyMime("https://cdn.test/unrelated","text/plain"))
 }
}
