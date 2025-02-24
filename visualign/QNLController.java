package visualign;

import java.awt.image.BufferedImage;
import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.prefs.Preferences;
import java.util.stream.IntStream;

import javax.imageio.ImageIO;

import data.Palette;
import data.SegLabel;
import data.Series;
import data.Slice;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.geometry.VPos;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.Alert;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.RadioMenuItem;
import javafx.scene.control.Slider;
import javafx.scene.control.Spinner;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.ButtonType;
import javafx.scene.control.SpinnerValueFactory.IntegerSpinnerValueFactory;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.image.Image;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.PixelWriter;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.FileChooser.ExtensionFilter;
import nonlin.Triangle;
import parsers.ITKLabel;
import parsers.JSON;
import slicer.Int32Slices;
import static data.Marker.marker;

public class QNLController implements ChangeListener<Number> {
    @FXML
    private VBox vbox;

    @FXML
    private Pane pane;

    @FXML
    private Canvas imgcnv;

    @FXML
    private Canvas ovlycnv;

    @FXML
    private Canvas lblcnv;

    @FXML
    private Slider opacitySlider;

    @FXML
    private ColorPicker outColor;

    @FXML
    private ColorPicker pinColor;

    @FXML
    private ColorPicker debColor;

    @FXML
    private RadioMenuItem debug;

    @FXML
    private Spinner<Integer> spn;
    
    @FXML
    private IntegerSpinnerValueFactory spnVal;

    @FXML
    void onClick(MouseEvent event) {
        System.out.println(event);
    }

    int pick = -1;
    List<Double> picked;
    double pickx;
    double picky;

    void updatePick() {
        if(slice==null)return;
        List<ArrayList<Double>> markers=slice.markers;
        pick = -1;
        picked = null;
        pickx = (mouseX - imgx) * slice.width / imgw;
        picky = (mouseY - imgy) * slice.height / imgh;
        double margin = slice.width * 10 / imgw;
        for (int i = markers.size() - 1; i >= 0; i--) {
            List<Double> m = markers.get(i);
            if (Math.abs(pickx - m.get(2)) < margin && Math.abs(picky - m.get(3)) < margin) {
                pick = i;
                picked = m;
                break;
            }
        }
    }

    double mouseX;
    double mouseY;
    boolean popbottom=true;

    @FXML
    void mouseMoved(MouseEvent event) {
        ovlycnv.requestFocus();
        mouseX = event.getX();
        mouseY = event.getY();
        drawPop();
    }
    
    private void drawPop() {
        int h=(int)lblcnv.getHeight();
        int w=(int)lblcnv.getWidth();
        if(popbottom && mouseY>h*0.8)
            popbottom=false;
        else if(!popbottom && mouseY<h*0.2)
            popbottom=true;
        GraphicsContext gc=lblcnv.getGraphicsContext2D();
        gc.clearRect(0, 0, w, h);
        int mx = (int)mouseX - imgx;
        int my = (int)mouseY - imgy;
        
        if(mx>=0 && my>=0 && mx<imgw && my<imgh) {
            SegLabel l=null;
            List<Triangle> triangles=slice.triangles;
            double fx = mx * slice.width / imgw;
            double fy = my * slice.height / imgh;
            for (int i = 0; i < triangles.size(); i++) {
                double t[] = triangles.get(i).transform(fx, fy);
                if (t != null) {
                    int xx = (int) (t[0] * x_overlay[0].length / slice.width);
                    int yy = (int) (t[1] * x_overlay.length / slice.height);
                    if(xx>=0 && yy>=0 && xx<x_overlay[0].length && yy<x_overlay.length)
                        l=palette.fullmap.get(x_overlay[yy][xx]);//!!
                }
            }
//            gc.strokeText(""+nlovly[mx+my*imgw], 100, 100);
            if(l!=null && l.index>0) {
                gc.setFill(Color.rgb(l.red, l.green, l.blue));
                gc.fillRect(0, popbottom?h-50:0, w, 50);
                gc.setFill(Color.BLACK);
                gc.setFont(Font.font("System",FontWeight.BOLD,30));
                gc.setTextAlign(TextAlignment.CENTER);
                gc.setTextBaseline(VPos.CENTER);
                gc.fillText(l.name, w/2, popbottom?h-25:25);
            }
        }
    }

    double basex, basey;

    @FXML
    void mousePressed(MouseEvent event) {
        updatePick();
        if (picked != null) {
            basex = picked.get(2);
            basey = picked.get(3);
        }
    }

    @FXML
    void mouseDragged(MouseEvent event) {
        mouseMoved(event);
        if (picked != null) {
            picked.set(2, basex + (mouseX - imgx) * slice.width / imgw - pickx);
            picked.set(3, basey + (mouseY - imgy) * slice.height / imgh - picky);
            slice.triangulate();
            reDraw();
        }
    }

//    List<Marker> markers = new ArrayList<Marker>();
    @FXML
    void keyPressed(KeyEvent event) {
        KeyCode kc=event.getCode();
        if(kc==KeyCode.LEFT)
            spnVal.decrement(1);
        if(kc==KeyCode.RIGHT)
            spnVal.increment(1);
    }

    @FXML
    void keyTyped(KeyEvent event) {
        if(slice==null)return;
        
        List<ArrayList<Double>> markers=slice.markers;
        List<Triangle> triangles=slice.triangles;

        updatePick();
        if (event.getCharacter().codePointAt(0) == 8 || event.getCharacter().codePointAt(0) == 127) {
//            updatePick();
            if (pick >= 0) {
                markers.remove(pick);
            }
        } else {
            if (pick >= 0) return;
            double mx = (mouseX - imgx) * slice.width / imgw;
            double my = (mouseY - imgy) * slice.height / imgh;
//            boolean found = false;
            for (int i = 0; i < triangles.size(); i++) {
                double xy[] = triangles.get(i).transform(mx, my);
                if (xy != null) {
                    markers.add(marker(xy[0], xy[1], mx, my));
//                    found = true;
                    break;
                }
            }
//            if (!found)
//                throw new RuntimeException();
        }
        slice.triangulate();
        reDraw();
    }

    @FXML
    void initialize() throws Exception {
        spn.getStyleClass().clear(); //!!
//        spn.getStyleableNode().setStyle("-fx-text-alignment: center;");
        
//        markers.add(new Marker(0, 0));
//        markers.add(new Marker(slice.width, 0));
//        markers.add(new Marker(0, slice.height));
//        markers.add(new Marker(slice.width, slice.height));
//        triangulate();

//        pane.setFocusTraversable(true);
//        imgcnv.setFocusTraversable(true);
//        ovlycnv.setFocusTraversable(true);
        pane.widthProperty().addListener(this);
        pane.heightProperty().addListener(this);
        pinColor.setValue(Color.BLACK);
        outColor.setValue(Color.DARKBLUE);
        debColor.setValue(Color.GREY);
//        palette = new ArrayList<SegLabel>();
//        try (FileReader fr = new FileReader("Segmentation.json")) {
//            JSON.mapList(JSON.parse(fr), palette, SegLabel.class, null);
//        }
//        fastpalette=new int[palette.size()];
//        for(int i=0;i<fastpalette.length;i++) {
//            SegLabel l=palette.get(i);
//            fastpalette[i]=((int)l.red << 16)+((int)l.green << 8)+(int)l.blue;
//        }
//        try (DataInputStream dis=new DataInputStream(new BufferedInputStream(new FileInputStream("R601_s172_BDA_NR_10x-Segmentation.flat")))){
//            byte bpp=dis.readByte();
//            if(bpp!=1)throw new Exception("BPP: "+bpp);
//            int ovlyw=dis.readInt();
//            int ovlyh=dis.readInt();
//            overlay=new int[ovlyh][ovlyw];
//            for(int y=0;y<ovlyh;y++)
//                for(int x=0;x<ovlyw;x++)
//                    overlay[y][x]=dis.readByte();
//        }
//        image = new Image("file:R601_s172_BDA_NR_10x.png");
//        
        opacitySlider.valueProperty().addListener(this);
        spnVal.valueProperty().addListener(this);
    }

    Series series;
    Slice slice;
    
    @Override
    public void changed(ObservableValue<? extends Number> arg0, Number arg1, Number arg2) {
        if(arg0==spnVal.valueProperty()) {
            loadView();
            return;
        }
        if (arg0 == pane.widthProperty() || arg0 == pane.heightProperty())
            drawImage();
        reDraw();
    }

    private Image image;
    int imgx, imgy, imgw, imgh;
    int rgb[];
    int nlovly[];

    int x_overlay[][];
    int fastoverlay[];
    
    public void drawImage() {
        GraphicsContext ctx = imgcnv.getGraphicsContext2D();
        int cw = (int) imgcnv.getWidth();
        int ch = (int) imgcnv.getHeight();
        ctx.clearRect(0, 0, cw, ch);
        
        if (slice == null)
            return; // !!
        
        int iw = (int) image.getWidth();
        int ih = (int) image.getHeight();

        imgx = imgy = 0;
        if (cw * ih < ch * iw) {
            imgh = ih * cw / iw;
            imgw = cw;
            imgy = (ch - imgh) / 2;
        } else {
            imgw = iw * ch / ih;
            imgh = ch;
            imgx = (cw - imgw) / 2;
        }
        
        rgb=new int[imgw*imgh];
        nlovly=new int[imgw*imgh];

        ctx.drawImage(image, imgx, imgy, imgw, imgh);
    }

    /*
     * Screen-space x: 0...imgx-1 y: 0...imgy-1
     */
    public int sample(int x, int y) {
        List<Triangle> triangles=slice.triangles;
        double fx = x * slice.width / imgw;
        double fy = y * slice.height / imgh;
        for (int i = 0; i < triangles.size(); i++) {
            double t[] = triangles.get(i).transform(fx, fy);
            if (t != null) {
                int xx = (int) (t[0] * x_overlay[0].length / slice.width);
                int yy = (int) (t[1] * x_overlay.length / slice.height);
                if(xx<0 || yy<0 || xx>=x_overlay[0].length || yy>=x_overlay.length)
                    return 0;
                //return overlay[yy][xx];
                return fastoverlay[xx+yy*x_overlay[0].length];
            }
        }
        return 0;// 0x80000000;
    }

    public void reDraw() {
        if(slice==null)return;//!!
        drawOvly();
        if (debug.isSelected())
            drawDebug();
        drawPins();
        drawPop();
    }

    public void drawOvly() {
        Arrays.fill(rgb, 0);
        IntStream.range(0, imgh).parallel().forEach(y -> {
            for (int x = 0; x < imgw; x++)
                nlovly[x+y*imgw]= sample(x, y);
        });
        
        int op = (int) opacitySlider.getValue() * 255 / 100;
        if (op < 255) {
            int a = op << 24;
            IntStream.range(0, imgh).parallel().forEach(y -> {
                for (int x = 0; x < imgw; x++) {
                    int o = nlovly[x+y*imgw];
                    if(o!=0)
                        rgb[x+y*imgw]=a+palette.fastcolors[o];
                }
            });
        } else {
            Color c = outColor.getValue();
            int a = 0xFF000000 + ((int) (c.getRed() * 255) << 16) + ((int) (c.getGreen() * 255) << 8)
                    + (int) (c.getBlue() * 255);
//            IntStream.range(1, imgh-1).parallel().forEach(y -> {
            for(int y=1; y<imgh-1;y++)
                for (int x = 1; x < imgw-1; x++) {
                    int o=nlovly[x+y*imgw];
                    if(nlovly[x+y*imgw-1] != o
                    || nlovly[x+y*imgw+1] != o
                    || nlovly[x+y*imgw-imgw] != o
                    || nlovly[x+y*imgw+imgw] != o)
                    rgb[x+y*imgw]= a;
                }
//            });
        }
        GraphicsContext ctx = ovlycnv.getGraphicsContext2D();
        ctx.clearRect(0, 0, imgcnv.getWidth(), imgcnv.getHeight());
        PixelWriter pw = ctx.getPixelWriter();
        pw.setPixels(imgx,imgy,imgw,imgh,PixelFormat.getIntArgbInstance(),rgb,0,imgw);
    }

    private void drawPins() {
        GraphicsContext ctx = ovlycnv.getGraphicsContext2D();
        ctx.setStroke(pinColor.getValue());
        ctx.setLineWidth(3);
        List<ArrayList<Double>> markers=slice.markers;
        for (int i = 0; i < markers.size(); i++) {
            List<Double> m = markers.get(i);
            double nx = m.get(2) * imgw / slice.width + imgx;
            double ny = m.get(3) * imgh / slice.height + imgy;
            ctx.strokeLine(nx, ny - 10, nx, ny + 10);
            ctx.strokeLine(nx - 10, ny, nx + 10, ny);
            double ox = m.get(0) * imgw / slice.width + imgx;
            double oy = m.get(1) * imgh / slice.height + imgy;
            ctx.strokeLine(ox, oy, nx, ny);
        }
    }

    private void drawDebug() {
        GraphicsContext ctx = ovlycnv.getGraphicsContext2D();
        ctx.setStroke(debColor.getValue());
        ctx.setLineWidth(1);
        List<Triangle> triangles=slice.triangles;
        for (int i = 0; i < triangles.size(); i++) {
            Triangle t = triangles.get(i);
            double ax = imgx + imgw * t.A.get(2) / slice.width;
            double ay = imgy + imgh * t.A.get(3) / slice.height;
            double bx = imgx + imgw * t.B.get(2) / slice.width;
            double by = imgy + imgh * t.B.get(3) / slice.height;
            double cx = imgx + imgw * t.C.get(2) / slice.width;
            double cy = imgy + imgh * t.C.get(3) / slice.height;
            ctx.strokeLine(ax, ay, bx, by);
            ctx.strokeLine(bx, by, cx, cy);
            ctx.strokeLine(cx, cy, ax, ay);
        }
    }

    @FXML
    void debug(ActionEvent event) {
        reDraw();
    }
    
    @FXML
    void exit(ActionEvent event) {
        Platform.exit();
    }

    String current;
    Palette palette;
    Int32Slices slicer;
    Path baseFolder;
    String filename;

    @FXML
    void open(ActionEvent event) throws Exception {
        Preferences prefs=Preferences.userRoot().node("/no/uio/nesys/visualign");
        File f=new File(prefs.get("lastDir", "/"));
        FileChooser fc = new FileChooser();
        if(f.exists())
            fc.setInitialDirectory(f);
        fc.setTitle("Pick JSON file");
        ExtensionFilter ef=new ExtensionFilter("QuickNII JSON files", "*.json");
        fc.getExtensionFilters().add(ef);
        //fc.setSelectedExtensionFilter(ef);
        /*File*/ f = fc.showOpenDialog(stage);
        if (f != null) {
            // !! unload
            baseFolder=f.getParentFile().toPath();
            prefs.put("lastDir", baseFolder.toString());
            filename=f.getName();
            filename=filename.substring(0, filename.length()-5);
            series = new Series();
            try (FileReader fr = new FileReader(f)) {
                Map<String, String> resolver = new HashMap<>();
                resolver.put("resolution", "target-resolution");
                JSON.mapObject(JSON.parse(fr), series, resolver);
            }
            if(series.slices.size()<1) {
                series=null;
                Alert a=new Alert(AlertType.ERROR, "This JSON file was not generated by QuickNII.", ButtonType.OK);
                a.showAndWait();
                return;
            }
            series.propagate();
            if(series.target==null) {
                DirectoryChooser dc=new DirectoryChooser();
                dc.setTitle("Old series data, please select an atlas folder from QNonLin");
                dc.setInitialDirectory(new File(System.getProperty("java.home")));
                File d=dc.showDialog(stage);
                if(d==null)return;
                series.target=d.getName();
            }
            if(current==null || !current.equals(series.target)) {
                current=series.target;
                palette=ITKLabel.parseLabels(current+File.separator+"labels.txt");
                slicer=new Int32Slices(current+File.separator+"labels.nii.gz");
            }
            if(series.resolution.size()==0) {
                series.resolution.add((double)slicer.XDIM);
                series.resolution.add((double)slicer.YDIM);
                series.resolution.add((double)slicer.ZDIM);
            }
            spnVal.setMin(1);
            spnVal.setMax(series.slices.size());
            spnVal.setValue(spnVal.getMax()/2);
//            loadView();
        }
    }
    
    void loadView() {
        if(series==null)
            return; //!!
        slice=series.slices.get(spnVal.getValue()-1);
        image=new Image("file:"+baseFolder.resolve(slice.filename));
        setTitle(slice.filename);
        slice.triangulate();
        drawImage();
        Double ouv[]=slice.anchoring.toArray(new Double[0]);
        x_overlay=slicer.getInt32Slice(ouv[0], ouv[1], ouv[2], ouv[3], ouv[4], ouv[5], ouv[6], ouv[7], ouv[8], false);
        fastoverlay=new int[x_overlay.length*x_overlay[0].length];
        for(int y=0;y<x_overlay.length;y++) {
            int l[]=x_overlay[y];
            for(int x=0;x<l.length;x++)
                fastoverlay[x+y*l.length]=palette.fullmap.get(l[x]).remap;
        }
        reDraw();
    }

//    @FXML
//    void save(ActionEvent event) throws IOException {
//        File f=baseFolder.resolve(filename+".json").toFile();
//        File b=baseFolder.resolve(filename+".bak.json").toFile();
//        if(b.exists())
//            System.out.println("delete bak:"+b.delete());
//        System.out.println("create bak:"+f.renameTo(b));
//        try(FileWriter fw=new FileWriter(f)){
//            series.toJSON(fw);
//        }
//    }

    @FXML
    void exprt(ActionEvent event) throws IOException {
        DirectoryChooser dc=new DirectoryChooser();
        dc.setInitialDirectory(baseFolder.toFile());
        dc.setTitle("Pick folder for exporting slices");
        File f=dc.showDialog(stage);
        if(f!=null) {
            List<Slice> slices=series.slices;
            int count=0;
            try(PrintWriter pw=new PrintWriter(f+File.separator+"report.tsv")){
//                pw.println("snr\tname\twidth\theight\ttotal\tsegmented\tcoverage%\tchanged\tchanged%");
                pw.println("snr\tname\tsegmented\tchanged\tstable%");
            for(int i=0;i<slices.size();i++) {
                Slice slice=slices.get(i);
                if(slice.markers.size()>0) {
                    count++;
                    String name=slice.filename.substring(0, slice.filename.lastIndexOf('.'));
                    Double ouv[]=slice.anchoring.toArray(new Double[0]);
                    int overlay[][]=slicer.getInt32Slice(ouv[0], ouv[1], ouv[2], ouv[3], ouv[4], ouv[5], ouv[6], ouv[7], ouv[8], false);
                    slice.triangulate();
                    List<Triangle> triangles=slice.triangles;
                    int h=overlay.length;
                    int w=overlay[0].length;
                    byte rgb[]=new byte[w*h*3];
                    int segmented=0;
                    int changed=0;
                    try(DataOutputStream dos=new DataOutputStream(new BufferedOutputStream(new FileOutputStream(f+File.separator+name+"_nl.flat")))){
                        boolean byt=palette.fastcolors.length<=256;
                        dos.writeByte(byt?1:2);
                        dos.writeInt(w);
                        dos.writeInt(h);
                        for(int y=0;y<h;y++)
                            for(int x=0;x<w;x++) {
//                                SegLabel c=palette.fullmap.get(overlay[y][x]);
                                SegLabel c=palette.fullmap.get(0);
                                double fx = x * slice.width / w;
                                double fy = y * slice.height / h;
                                for (int j = 0; j < triangles.size(); j++) {
                                    double t[] = triangles.get(j).transform(fx, fy);
                                    if (t != null) {
                                        int xx = (int) (t[0] * w / slice.width);
                                        int yy = (int) (t[1] * h / slice.height);
                                        if(xx>=0 && yy>=0 && xx<overlay[0].length && yy<overlay.length)
                                            c=palette.fullmap.get(overlay[yy][xx]);//!!
                                        break;
                                    }
                                }
                                rgb[(x+y*w)*3]=(byte)c.red;
                                rgb[(x+y*w)*3+1]=(byte)c.green;
                                rgb[(x+y*w)*3+2]=(byte)c.blue;
                                if(byt)
                                    dos.writeByte(c.remap);
                                else
                                    dos.writeShort(c.remap);
                                if(overlay[y][x]!=0 || c.index!=0)
                                    segmented++;
                                if(overlay[y][x]!=c.index)
                                    changed++;
                            }
//                        pw.println((int)slice.nr+"\t"+slice.filename+"\t"+overlay[0].length+"\t"+overlay.length+"\t"+
//                            overlay[0].length*overlay.length+"\t"+segmented+"\t"+segmented*100/overlay[0].length/overlay.length+"%\t"+
//                            changed+"\t"+changed*100/segmented+"%");
                        pw.println((int)slice.nr+"\t"+slice.filename+"\t"+segmented+"\t"+changed+"\t"+(segmented-changed)*100/segmented+"%");
                    }
                    BufferedImage bi=new BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR);
                    bi.getRaster().setDataElements(0, 0, w, h, rgb);
                    ImageIO.write(bi, "png", new File(f+File.separator+name+"_nl.png"));
                }
            }
            }
            Alert a=new Alert(AlertType.INFORMATION, "Done. "+(count==0?"No":count)+" non-linear segmentation"+(count!=1?"s":"")+" exported.");
            a.showAndWait();
        }
    }

    @FXML
    void saveas(ActionEvent event) throws IOException {
        FileChooser fc = new FileChooser();
        fc.setInitialDirectory(baseFolder.toFile());
        fc.setTitle("Pick JSON file");
        ExtensionFilter ef=new ExtensionFilter("JSON files", "*.json");
        fc.getExtensionFilters().add(ef);
        File f=fc.showSaveDialog(stage);
        if(f!=null) {
            try(FileWriter fw=new FileWriter(f)){
                series.toJSON(fw);
            }
        }
    }

    @FXML
    void close(ActionEvent event) {
        if(series==null)return;
        Alert a=new Alert(AlertType.WARNING,"Do you want to close series?",ButtonType.YES,ButtonType.NO);
        a.showAndWait().ifPresent(b->{
            if(b==ButtonType.YES) {
                series=null;
                slice=null;
                baseFolder=null;
                filename=null;
                setTitle(null);
                drawImage();
                GraphicsContext ctx = ovlycnv.getGraphicsContext2D();
                ctx.clearRect(0, 0, imgcnv.getWidth(), imgcnv.getHeight());
                spnVal.setMax(1);
                imgw=0;
                imgh=0;
            }
        });
    }

    @FXML
    void rePin(ActionEvent event) {
        drawPins();
    }

    @FXML
    void reOvly(ActionEvent event) {
        reDraw();
    }

    @FXML
    void reDebug(ActionEvent event) {
        drawDebug();
        drawPins();
    }
    
    @FXML
    void goFirst(ActionEvent event) {
//        if(series==null)return;
        spnVal.setValue(1);
    }
    
    @FXML
    void goLast(ActionEvent event) {
        if(series==null)return;
        spnVal.setValue(series.slices.size());
    }
    
    @FXML
    void less(ActionEvent event) {
//        if(series==null)return;
        spnVal.decrement(1);
    }
    
    @FXML
    void more(ActionEvent event) {
//        if(series==null)return;
        spnVal.increment(1);
    }
    
    @FXML
    void less10(ActionEvent event) {
//        if(series==null)return;
        spnVal.decrement(10);
    }
    
    @FXML
    void more10(ActionEvent event) {
//        if(series==null)return;
        spnVal.increment(10);
    }

    @FXML
    void clear(ActionEvent event) {
        if(series==null)return;
        Alert a=new Alert(AlertType.WARNING,"Proceed with dropping all markers from current section?",ButtonType.YES,ButtonType.NO);
        a.showAndWait().ifPresent(b->{
            if(b==ButtonType.YES) {
                slice.markers.clear();
                slice.triangulate();
                reDraw();
            }
        });
    }
    
    @FXML
    void about(ActionEvent event) {
        String text=title+"\n\nVisuAlign is developed at the Neural Systems Laboratory, Institute of Basic Medical Sciences, University of Oslo (Norway), with funding from the European Union�s Horizon 2020 Framework Programme for Research and Innovation under the Framework Partnership Agreement No. 650003 (HBP FPA).";
        new Alert(AlertType.NONE, text, ButtonType.CLOSE).showAndWait();
    }
    
    Stage stage;
    public void setTitle(String filename) {
        stage.setTitle(filename==null?title:(title+": "+filename));
    }
    public static final String version="v0.8";
    public static final String title="VisuAlign - NonLinear "+version;
}
