package com.hanco.buildai.blueprint;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Converts a user-selected PNG/JPEG into a vertical ONE-BLOCK-DEEP Minecraft
 * voxel picture. Runs locally. Never calls an AI, an image generation API,
 * or edits the current world. The result uses ordinary BuildAI preview,
 * schematic saving and Creative/Survival placement.
 */
public final class PixelArtConverter {
    private PixelArtConverter(){}
    public static final int MAX_SIDE=128;
    public static final long MAX_BYTES=20L*1024L*1024L;
    public static final int MAX_SOURCE_SIDE=4096;
    private static final int TRANSPARENT_ALPHA=128;

    public enum Material { CONCRETE, WOOL }
    public record Swatch(String block,int rgb){}
    public record Result(Blueprint blueprint,int[][] previewArgb,
                         int sourceWidth,int sourceHeight,int colorCount){
        public int width(){return blueprint.width;}
        public int height(){return blueprint.height;}
    }

    // Approximate Minecraft Java 1.21.1 normal-light colors. Each entry is
    // a valid vanilla block; gamma-aware nearest mapping can be expanded.
    private static final int[] COLORS={
        0xE9E9E9,0xE87925,0xB35DBF,0x6E9FD7,
        0xD9BE37,0x70B33D,0xDE8DA6,0x3F4248,
        0x969696,0x187A9F,0x81429F,0x384F9C,
        0x785235,0x567D2E,0x9B2A25,0x202126
    };
    private static final String[] DYES={
        "white","orange","magenta","light_blue",
        "yellow","lime","pink","gray","light_gray",
        "cyan","purple","blue","brown","green","red","black"
    };
    private static final int[] WOOL_COLORS={
        0xE9E9E9,0xF09842,0xB46EC1,0x76A5DB,
        0xE5D550,0x7DC950,0xE99CA9,0x45464C,
        0xA0A0A0,0x2587A1,0x944DAF,0x455FB0,
        0x825B3A,0x608C39,0xAD3530,0x26272C
    };

    public static List<Swatch> palette(Material material){
        int[] colors=material==Material.WOOL?WOOL_COLORS:COLORS;
        java.util.ArrayList<Swatch> palette=new java.util.ArrayList<>();
        for(int i=0;i<DYES.length;i++)
            palette.add(new Swatch("minecraft:"+DYES[i]+"_"+
                (material==Material.WOOL?"wool":"concrete"),colors[i]));
        return List.copyOf(palette);
    }

    public static Result fromFile(Path path,int requestedWidth,
                                 Material material,boolean smooth,
                                 boolean transparent) throws IOException {
        if(path==null)throw new IOException("Choose an image");
        Path selected=path.toAbsolutePath().normalize();
        String filename=selected.getFileName().toString().toLowerCase(Locale.ROOT);
        if(!(filename.endsWith(".png")||filename.endsWith(".jpg")||
             filename.endsWith(".jpeg")))
            throw new IOException("Choose a PNG, JPG or JPEG image");
        if(!Files.isRegularFile(selected))throw new IOException("Image file not found");
        long bytes=Files.size(selected);
        if(bytes==0||bytes>MAX_BYTES)
            throw new IOException("Image file must be between 1 byte and 20 MB");
        try(ImageInputStream input=ImageIO.createImageInputStream(selected.toFile())){
            if(input==null)throw new IOException("Unable to open image");
            Iterator<ImageReader> available=ImageIO.getImageReaders(input);
            if(!available.hasNext())throw new IOException("Unsupported image format");
            ImageReader reader=available.next();
            try{
                reader.setInput(input,true,true);
                int w=reader.getWidth(0),h=reader.getHeight(0);
                if(w<1||h<1||w>MAX_SOURCE_SIDE||h>MAX_SOURCE_SIDE)
                    throw new IOException("Image dimensions must not exceed 4096×4096");
                BufferedImage image=reader.read(0);
                if(image==null)throw new IOException("Could not decode image");
                return convert(image,selected.getFileName().toString(),
                    requestedWidth,material,smooth,transparent);
            }finally{reader.dispose();}
        }catch(IllegalArgumentException e){
            throw new IOException("Image could not be decoded: "+e.getMessage(),e);
        }
    }

    public static Result convert(BufferedImage source,String name,int requestedWidth,
                                 Material material,boolean smooth,
                                 boolean transparent){
        if(source==null||material==null)
            throw new IllegalArgumentException("Source image and palette required");
        int ow=source.getWidth(),oh=source.getHeight();
        if(ow<1||oh<1||ow>MAX_SOURCE_SIDE||oh>MAX_SOURCE_SIDE)
            throw new IllegalArgumentException("Source image out of range");
        if(requestedWidth<8||requestedWidth>MAX_SIDE)
            throw new IllegalArgumentException("Width must be 8–128 blocks");
        if(name==null||name.isBlank())name="Uploaded_Image";
        double factor=Math.min((double)requestedWidth/ow,(double)MAX_SIDE/oh);
        int w=Math.max(1,Math.min(MAX_SIDE,(int)Math.round(ow*factor)));
        int h=Math.max(1,Math.min(MAX_SIDE,(int)Math.round(oh*factor)));
        // Create a premultiplied-intermediate so antialiased transparent
        // edges do not contaminate their neighboring visible colors.
        BufferedImage scaled=new BufferedImage(w,h,BufferedImage.TYPE_INT_ARGB);
        Graphics2D g=scaled.createGraphics();
        try{
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                smooth?RenderingHints.VALUE_INTERPOLATION_BILINEAR:
                    RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g.drawImage(source,0,0,w,h,null);
        }finally{g.dispose();}
        Blueprint blueprint=new Blueprint("PixelArt_"+name.replaceAll(
            "(?i)\\.(png|jpe?g)$","").replaceAll("[^A-Za-z0-9_-]","_"),
            w,h,1);
        List<Swatch> options=palette(material);
        int[][] preview=new int[h][w];
        java.util.Set<String> used=new java.util.HashSet<>();
        for(int iy=0;iy<h;iy++)for(int ix=0;ix<w;ix++){
            int rgb=scaled.getRGB(ix,iy);
            boolean clear=(rgb>>>24)<TRANSPARENT_ALPHA;
            if(clear&&transparent){
                preview[iy][ix]=0x00000000;
                continue;
            }
            if(clear)rgb=0xFFFFFFFF;
            int closest=nearest(rgb,options);
            Swatch block=options.get(closest);
            used.add(block.block());
            preview[iy][ix]=0xFF000000|block.rgb();
            // PNG origin is top-left. Minecraft Y is bottom-up.
            blueprint.set(ix,h-1-iy,0,block.block());
        }
        blueprint.warnings.add("PIXEL ART: Local image conversion, not AI-generated. "+
            "Image colors approximated with 16 Minecraft "+
            (material==Material.WOOL?"wool":"concrete")+" shades.");
        blueprint.warnings.add("PIXEL ART: Vertical single-block-thick wall, "+
            w+"×"+h+" blocks. Preview orientation and placement before construction.");
        if(transparent)
            blueprint.warnings.add("PIXEL ART: Transparent pixels were left "+
                "AIR; support and foreground/background are player-controlled.");
        return new Result(blueprint,preview,ow,oh,used.size());
    }
    private static int nearest(int rgb,List<Swatch> swatches){
        // Compare in approximate linear-light color space to reduce severe
        // perceptual errors with deep colors compared in naive sRGB.
        double r=linear((rgb>>>16)&255),g=linear((rgb>>>8)&255),
            b=linear(rgb&255);
        double sourceHue=hue(rgb),sourceSaturation=saturation(rgb);
        double best=Double.POSITIVE_INFINITY;int index=0;
        for(int i=0;i<swatches.size();i++){
            int c=swatches.get(i).rgb();
            double dr=r-linear((c>>>16)&255),
                   dg=g-linear((c>>>8)&255),db=b-linear(c&255);
            // Hue matters for highly saturated logos: otherwise pure red
            // can incorrectly map to bright orange just because darker red
            // concrete has a lower luminance than the input pixel.
            double hueDiff=Math.abs(sourceHue-hue(c));
            hueDiff=Math.min(hueDiff,1d-hueDiff);
            double saturationWeight=Math.min(sourceSaturation,saturation(c));
            double huePenalty=.22*Math.pow(hueDiff/.07,2)*saturationWeight;
            double delta=.26*dr*dr+.67*dg*dg+.07*db*db+huePenalty;
            if(delta<best){best=delta;index=i;}
        }
        return index;
    }
    private static double saturation(int color){
        int r=(color>>>16)&255,g=(color>>>8)&255,b=color&255;
        int hi=Math.max(r,Math.max(g,b)),lo=Math.min(r,Math.min(g,b));
        return hi==0?0d:(double)(hi-lo)/hi;
    }
    private static double hue(int color){
        double r=((color>>>16)&255)/255d,
            g=((color>>>8)&255)/255d,b=(color&255)/255d;
        double max=Math.max(r,Math.max(g,b)),min=Math.min(r,Math.min(g,b));
        double diff=max-min;
        if(diff==0)return 0d;
        double value;
        if(max==r)value=((g-b)/diff)%6d;
        else if(max==g)value=(b-r)/diff+2d;
        else value=(r-g)/diff+4d;
        value/=6d;
        return value<0?value+1d:value;
    }
    private static double linear(int component){
        double c=component/255d;
        return c<=0.04045?c/12.92:Math.pow((c+0.055)/1.055,2.4);
    }
}
