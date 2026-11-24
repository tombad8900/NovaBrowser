package app.nova.browser
import org.junit.Assert.*
import org.junit.Test
class SessionAttachmentsTest {
 @Test fun delayedReadyAttachesExistingAndLaterSessionsOnce(){
  val gate=SessionAttachments<String>();val got=mutableListOf<String>()
  gate.register("early");assertTrue(got.isEmpty());gate.ready{got.add(it)}
  gate.register("late");gate.register("early");gate.ready{throw AssertionError("duplicate ready")}
  assertEquals(listOf("early","late"),got)
 }
 @Test fun closedBeforeReadyNeverAttaches(){
  val gate=SessionAttachments<String>();val got=mutableListOf<String>()
  gate.register("closed");gate.unregister("closed");gate.register("live");gate.ready{got.add(it)}
  assertEquals(listOf("live"),got)
 }
 @Test fun clearDropsPendingSessions(){
  val gate=SessionAttachments<String>();gate.register("old");gate.clear();val got=mutableListOf<String>();gate.ready{got.add(it)}
  assertTrue(got.isEmpty())
 }
}
