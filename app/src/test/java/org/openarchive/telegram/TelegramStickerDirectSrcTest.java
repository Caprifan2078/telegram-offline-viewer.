package org.openarchive.telegram;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.Test;
import static org.junit.Assert.*;
public class TelegramStickerDirectSrcTest {
    @Test public void directSourceTgsMustNotDisappear(){
        Element msg=Jsoup.parse("<div class='message'><div class='body'><div class='sticker_wrap'><img src='stickers/demo.tgs'></div></div></div>").selectFirst(".message");
        assertEquals(1,TelegramImporter.findMedia(msg).size());
        assertEquals("sticker",TelegramImporter.findMedia(msg).get(0).kind);
    }
    @Test public void webmStickerMustNotBecomeOrdinaryVideo(){
        Element msg=Jsoup.parse("<div class='message'><div class='body'><div class='sticker_wrap'><img src='stickers/demo.webm'></div></div></div>").selectFirst(".message");
        assertEquals(1,TelegramImporter.findMedia(msg).size());
        assertEquals("sticker",TelegramImporter.findMedia(msg).get(0).kind);
    }
    @Test public void photoThumbMustNotDuplicatePhoto(){
        Element msg=Jsoup.parse("<div class='message'><div class='body'><div class='photo_wrap'><a href='photos/one.jpg'><img src='photos/one_thumb.jpg'></a></div></div></div>").selectFirst(".message");
        assertEquals(1,TelegramImporter.findMedia(msg).size());
        assertEquals("photos/one.jpg",TelegramImporter.findMedia(msg).get(0).path);
    }
}