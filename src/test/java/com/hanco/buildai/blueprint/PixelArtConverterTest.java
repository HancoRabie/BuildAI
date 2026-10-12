package com.hanco.buildai.blueprint;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

public class PixelArtConverterTest {
    @Test void photoResizesToRequestedWidthAndKeepsTopAtHighMinecraftY(){
        BufferedImage image=new BufferedImage(2,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0xFFFF0000);
        image.setRGB(1,0,0xFFFF0000);
        image.setRGB(0,1,0xFF000000);
        image.setRGB(1,1,0xFF000000);
        var output=PixelArtConverter.convert(image,"Test.png",16,
            PixelArtConverter.Material.CONCRETE,false,true);
        assertEquals(16,output.width());
        assertEquals(16,output.height());
        assertTrue(output.blueprint().name.startsWith("PixelArt_Test"));
        assertEquals("minecraft:red_concrete",output.blueprint().get(0,15,0));
        assertEquals("minecraft:black_concrete",output.blueprint().get(0,0,0));
        assertEquals(256,output.blueprint().solidCount());
    }

    @Test void transparentImagePixelsBecomeAirRatherThanBlack(){
        BufferedImage image=new BufferedImage(2,1,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0,0,0x00000000);
        image.setRGB(1,0,0xFF000000);
        var output=PixelArtConverter.convert(image,"transparent.png",16,
            PixelArtConverter.Material.WOOL,false,true);
        assertEquals(16,output.width());
        assertEquals(8,output.height());
        assertEquals("minecraft:air",output.blueprint().get(0,0,0));
        assertEquals("minecraft:black_wool",output.blueprint().get(15,0,0));
        assertEquals(0,output.previewArgb()[0][0]);
        assertEquals(16*8/2,output.blueprint().solidCount());
    }

    @Test void opaqueBackgroundOptionMakesTransparentPixelsWhite(){
        BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_ARGB);
        var output=PixelArtConverter.convert(image,"blank.png",16,
            PixelArtConverter.Material.CONCRETE,false,false);
        assertEquals("minecraft:white_concrete",output.blueprint().get(0,0,0));
        assertEquals(256,output.blueprint().solidCount());
    }

    @Test void portraitIsBoundedWithoutDistortingAspectRatio(){
        BufferedImage image=new BufferedImage(10,100,BufferedImage.TYPE_INT_RGB);
        var result=PixelArtConverter.convert(image,"portrait.png",128,
            PixelArtConverter.Material.WOOL,true,false);
        assertEquals(128,result.height());
        assertEquals(13,result.width());
        assertEquals(1,result.blueprint().length);
    }

    @Test void readsRealPngAndRejectsUnsupportedImageTypes(@TempDir Path tmp)
        throws Exception{
        Path image=tmp.resolve("art.png");
        BufferedImage pixels=new BufferedImage(4,2,BufferedImage.TYPE_INT_RGB);
        assertTrue(ImageIO.write(pixels,"png",image.toFile()));
        var converted=PixelArtConverter.fromFile(image,32,
            PixelArtConverter.Material.CONCRETE,true,false);
        assertEquals(32,converted.width());
        assertEquals(16,converted.height());
        assertEquals(4,converted.sourceWidth());
        assertEquals(2,converted.sourceHeight());
        Path bad=tmp.resolve("data.txt");
        Files.writeString(bad,"not an image");
        assertThrows(java.io.IOException.class,()->
            PixelArtConverter.fromFile(bad,32,PixelArtConverter.Material.WOOL,true,true));
        Path fake=tmp.resolve("bad.png");
        Files.writeString(fake,"not a valid png");
        assertThrows(java.io.IOException.class,()->
            PixelArtConverter.fromFile(fake,32,PixelArtConverter.Material.WOOL,true,true));
    }

    @Test void rejectsOversizedOrInvalidWidths(){
        BufferedImage image=new BufferedImage(1,1,BufferedImage.TYPE_INT_RGB);
        assertThrows(IllegalArgumentException.class,()->
            PixelArtConverter.convert(image,"bad",129,
                PixelArtConverter.Material.CONCRETE,true,false));
        assertThrows(IllegalArgumentException.class,()->
            PixelArtConverter.convert(image,"bad",7,
                PixelArtConverter.Material.CONCRETE,true,false));
    }
}
