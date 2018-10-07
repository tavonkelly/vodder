package me.tavon.vodder.encoding;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

public class ImageCache {

    private Map<String, BufferedImage> imageMap = new HashMap<>(); // TODO This may hog memory depending on the image size. Most are small so it 'should' be fine
    private OkHttpClient client;

    public ImageCache(OkHttpClient client) {
        this.client = client;
    }

    public BufferedImage getImage(String url) throws Exception {
        if (imageMap.containsKey(url)) {
            return imageMap.get(url);
        }

        if (url.endsWith(".svg")) {
            BufferedImage image = toBufferedImage(transcodeSVGDocument(new URL(url), 24, 24));
            imageMap.put(url, image);
            return image;
        }

        Request request = new Request.Builder()
                .url(url)
                .get()
                .addHeader("Cache-Control", "no-cache")
                .build();

        Response response = client.newCall(request).execute();
        ResponseBody responseBody = response.body();

        if (response.code() != 200 || responseBody == null) {
            throw new Exception("got non-200 or empty body");
        }

        BufferedImage image = ImageIO.read(responseBody.byteStream());
        response.close();

        imageMap.put(url, image);
        return image;
    }

    // Stolen from https://www.mail-archive.com/batik-users@xml.apache.org/msg00775.html
    private Image transcodeSVGDocument(URL url, int x, int y) {
//        // Create a PNG transcoder.
//        Transcoder t = new PNGTranscoder();
//
//        // Set the transcoding hints.
//        t.addTranscodingHint(PNGTranscoder.KEY_WIDTH, new Float(x));
//        t.addTranscodingHint(PNGTranscoder.KEY_HEIGHT, new Float(y));
//
//        // Create the transcoder input.
//        TranscoderInput input = new TranscoderInput(url.toString());
//
//        ByteArrayOutputStream ostream = null;
//        try {
//            // Create the transcoder output.
//            ostream = new ByteArrayOutputStream();
//            TranscoderOutput output = new TranscoderOutput(ostream);
//
//            // Save the image.
//            t.transcode(input, output);
//
//            // Flush and close the stream.
//            ostream.flush();
//            ostream.close();
//        } catch (Exception ex) {
//            ex.printStackTrace();
//        }
//
//        // Convert the byte stream into an image.
//        byte[] imgData = ostream.toByteArray();
//        Image img = Toolkit.getDefaultToolkit().createImage(imgData);
//
//        // Wait until the entire image is loaded.
//        MediaTracker tracker = new MediaTracker(new JPanel());
//        tracker.addImage(img, 0);
//        try {
//            tracker.waitForID(0);
//        } catch (InterruptedException ex) {
//            ex.printStackTrace();
//        }
//
//        // Return the newly rendered image.
//
////        JFrame frameX = new JFrame();
////        frameX.getContentPane().setLayout(new FlowLayout());
////        frameX.getContentPane().add(new JLabel(new ImageIcon(img)));
////        frameX.pack();
////        frameX.setVisible(true);
//
//        return img;
        return new BufferedImage(0, 0, BufferedImage.TYPE_INT_RGB);
    }

    // Stolen from https://stackoverflow.com/questions/13605248/java-converting-image-to-bufferedimage
    private BufferedImage toBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }

        // Create a buffered image with transparency
        BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

        // Draw the image on to the buffered image
        Graphics2D bGr = bimage.createGraphics();
        bGr.drawImage(img, 0, 0, null);
        bGr.dispose();

        // Return the buffered image
        return bimage;
    }

}
