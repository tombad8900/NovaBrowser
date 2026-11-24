package app.nova.browser
import org.junit.Test
import org.junit.Assert.*
class TorrentMetadataTest {
 @Test fun boundedRead(){assertArrayEquals(byteArrayOf(1,2,3),readTorrentMetadata(byteArrayOf(1,2,3).inputStream()));try{readTorrentMetadata(ByteArray(4*1024*1024+1).inputStream());fail("Oversize torrent")}catch(expected:IllegalArgumentException){}}
}
