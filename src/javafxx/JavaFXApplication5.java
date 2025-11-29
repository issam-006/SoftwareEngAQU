package javafxx;

import javafx.animation.*;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.InnerShadow;
import javafx.scene.layout.*;
import javafx.scene.paint.*;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Locale;

public class JavaFXApplication5 extends Application {

    // ▼ عدّل رابط الملف هنا (مثال: APK, EXE, ZIP...)
    private static final String DOWNLOAD_URL = "https://speed.hetzner.de/100MB.bin";

    private enum State { IDLE, LOADING, COMPLETE }
    private State state = State.IDLE;

    private VBox content;
    private StackPane techButton;

    private Label btnLabel, progressLabel, nextLabel, messageBox;
    private CheckBox allowAccess;

    // الدائرة ومظهرها
    private Circle baseCircle, neonRing, glassTop;
    private StackPane core; // يحتوي عناصر الدائرة ويأخذ clip

    // حالة الاكتمال
    private Rectangle nextRect;

    // خصائص القياس
    private final DoubleProperty baseSize = new SimpleDoubleProperty(220);

    // أنيميشنات الواجهة
    private Timeline neonPulse;      // نبض النيون أثناء التحميل
    private Timeline dotsAnim;       // تحريك نقاط "Downloading..."

    // تقدم التحميل
    private final LongProperty bytesTotal   = new SimpleLongProperty(-1); // من Content-Length (قد يكون -1)
    private final LongProperty bytesDone    = new SimpleLongProperty(0);
    private final DoubleProperty speedMBps  = new SimpleDoubleProperty(0.0); // سرعة حقيقية (MB/s) متغيرة
    private final IntegerProperty percent   = new SimpleIntegerProperty(0);

    // مهمة التحميل
    private Thread downloadThread;

    @Override
    public void start(Stage stage) {
        // خلفية رمادي/رصاصي
        StackPane root = new StackPane();
        root.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#2E3136")),
                        new Stop(1, Color.web("#3C4047"))),
                CornerRadii.EMPTY, Insets.EMPTY)));

        // المحتوى المركزي
        content = new VBox(22);
        content.setAlignment(Pos.CENTER);
        content.setFillWidth(false);

        messageBox = buildMessage();
        techButton = buildTechButton();

        allowAccess = new CheckBox("Allow access to files");
        allowAccess.setStyle("-fx-font-size:16px;-fx-text-fill:#CBD5E1;-fx-mark-color:#22D3EE;");

        content.getChildren().addAll(messageBox, techButton, allowAccess);
        StackPane wrapper = new StackPane(content);
        wrapper.setPadding(new Insets(30));
        root.getChildren().add(wrapper);

        Scene scene = new Scene(root, 960, 640);

        // حجم الزر يتبع أصغر بُعد
        baseSize.bind(Bindings.createDoubleBinding(
                () -> clamp(180, Math.min(scene.getWidth(), scene.getHeight()) * 0.28, 320),
                scene.widthProperty(), scene.heightProperty()));
        baseSize.addListener((o, ov, nv) -> resizeTech(nv.doubleValue()));
        resizeTech(baseSize.get());

        stage.setTitle("Tech Download – JavaFX");
        stage.setScene(scene);
        stage.show();
    }

    /* ================= بناء العناصر ================ */

    private Label buildMessage() {
        Label m = new Label();
        m.setVisible(false);
        m.setManaged(false);
        m.setWrapText(true);
        m.setAlignment(Pos.CENTER);
        m.setMaxWidth(520);
        m.setPadding(new Insets(10,16,10,16));
        styleInfo(m);
        return m;
    }

    private StackPane buildTechButton() {
        StackPane btn = new StackPane();
        btn.setCursor(Cursor.HAND);
        btn.setPickOnBounds(false);

        // نواة الدائرة + قصّ لعدم خروج الألوان
        core = new StackPane();
        core.setPickOnBounds(false);

        baseCircle = new Circle(110);
        baseCircle.setStroke(Color.TRANSPARENT);
        baseCircle.setFill(new RadialGradient(
                0, 0, 0.5, 0.5, 0.65, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#6B7280")),
                new Stop(0.55, Color.web("#4B5563")),
                new Stop(1, Color.web("#374151"))
        ));
        baseCircle.setEffect(new InnerShadow(10, Color.rgb(0,0,0,0.25)));

        glassTop = new Circle(110 * 0.86);
        glassTop.setFill(new RadialGradient(0,0,0.5,0.5,0.9,true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.rgb(255,255,255,0.20)),
                new Stop(1, Color.rgb(255,255,255,0.06))));
        glassTop.setTranslateY(-110 * 0.18);

        Circle clip = new Circle(110);
        clip.centerXProperty().bind(core.widthProperty().divide(2));
        clip.centerYProperty().bind(core.heightProperty().divide(2));
        clip.radiusProperty().bind(baseCircle.radiusProperty());
        core.setClip(clip);

        core.getChildren().addAll(baseCircle, glassTop);

        neonRing = new Circle(110);
        neonRing.setFill(Color.TRANSPARENT);
        neonRing.setStroke(new LinearGradient(0,0,1,0,true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#22D3EE")), new Stop(1, Color.web("#0EA5E9"))));
        neonRing.setStrokeWidth(2.0);
        neonRing.setEffect(new DropShadow(12, Color.rgb(34,211,238,0.35)));

        btnLabel = new Label("Download App");
        btnLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        btnLabel.setTextFill(Color.web("#E5E7EB"));

        // سطر حالة: نسبة (إن توفرت) • المحمّل/الإجمالي • السرعة • ETA
        progressLabel = new Label();
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
        progressLabel.setTextFill(Color.web("#CBD5E1"));
        progressLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, 13));

        // «Next»
        nextRect = new Rectangle(320, 64);
        nextRect.setArcWidth(20);
        nextRect.setArcHeight(20);
        nextRect.setFill(new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#0F172A")), new Stop(1, Color.web("#1E293B"))));
        nextRect.setStroke(Color.web("#22D3EE"));
        nextRect.setStrokeWidth(1.6);
        nextRect.setVisible(false);
        nextRect.setManaged(false);
        nextRect.setEffect(new InnerShadow(10, Color.rgb(34,211,238,0.30)));
        StackPane.setAlignment(nextRect, Pos.CENTER);

        nextLabel = new Label("Next");
        nextLabel.setFont(Font.font("System", FontWeight.BOLD, 20));
        nextLabel.setTextFill(Color.web("#E5E7EB"));
        nextLabel.setVisible(false);
        nextLabel.setManaged(false);
        StackPane.setAlignment(nextLabel, Pos.CENTER);

        // تحديث نص الحالة مع كل تغير
        percent.addListener((o, ov, nv) -> updateProgressLabel());
        bytesDone.addListener((o, ov, nv) -> updateProgressLabel());
        bytesTotal.addListener((o, ov, nv) -> updateProgressLabel());
        speedMBps.addListener((o, ov, nv) -> updateProgressLabel());

        // تهيئة نص البداية
        updateProgressLabel();

        btn.getChildren().addAll(core, neonRing, btnLabel, progressLabel, nextRect, nextLabel);

        // Hover
        btn.setOnMouseEntered(e -> { if (state == State.IDLE) scale(btn, 1.035, 160); });
        btn.setOnMouseExited(e -> scale(btn, 1.0, 180));

        // Click
        btn.setOnMouseClicked(e -> onButtonClick());

        // نبض نيون + نقاط
        neonPulse = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(neonRing.strokeWidthProperty(), 2.0),
                        new KeyValue(neonRing.effectProperty(), new DropShadow(10, Color.rgb(34,211,238,0.35)))),
                new KeyFrame(Duration.millis(450),
                        new KeyValue(neonRing.strokeWidthProperty(), 3.2),
                        new KeyValue(neonRing.effectProperty(), new DropShadow(18, Color.rgb(34,211,238,0.55)))),
                new KeyFrame(Duration.millis(900),
                        new KeyValue(neonRing.strokeWidthProperty(), 2.0),
                        new KeyValue(neonRing.effectProperty(), new DropShadow(10, Color.rgb(34,211,238,0.35))))
        );
        neonPulse.setCycleCount(Animation.INDEFINITE);

        dotsAnim = new Timeline(
                new KeyFrame(Duration.ZERO, e -> btnLabel.setText("Downloading.")),
                new KeyFrame(Duration.millis(400), e -> btnLabel.setText("Downloading..")),
                new KeyFrame(Duration.millis(800), e -> btnLabel.setText("Downloading..."))
        );
        dotsAnim.setCycleCount(Animation.INDEFINITE);

        return btn;
    }

    /* ================= تفاعل ================= */

    private void onButtonClick() {
        if (state == State.LOADING) return;

        if (state == State.COMPLETE) {
            pulse(nextRect);
            showMessage("Simulating 'Next' step...", false);
            return;
        }

        if (!allowAccess.isSelected()) {
            shake(techButton);
            showMessage("Please check 'Allow access to files' to proceed.", true);
            return;
        }

        startDownload();
    }

    private void startDownload() {
        state = State.LOADING;
        techButton.setCursor(Cursor.WAIT);

        btnLabel.setText("Downloading...");
        btnLabel.setTextFill(Color.web("#D1D5DB"));

        progressLabel.setVisible(true); progressLabel.setManaged(true);

        neonPulse.playFromStart();
        dotsAnim.playFromStart();

        // اختيار مكان الحفظ
        FileChooser fc = new FileChooser();
        fc.setTitle("Save Downloaded File");
        fc.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All files", "*.*")
        );
        File saveTo = fc.showSaveDialog(techButton.getScene().getWindow());
        if (saveTo == null) {
            // ألغى الاختيار
            showMessage("Download canceled by user.", true);
            cancelAndReset();
            return;
        }

        // تشغيل التحميل في Thread منفصل
        downloadThread = new Thread(() -> downloadFile(DOWNLOAD_URL, saveTo));
        downloadThread.setDaemon(true);
        downloadThread.start();
    }

    private void downloadFile(String urlStr, File outFile) {
        HttpURLConnection conn = null;
        try (BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(outFile))) {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);
            conn.connect();

            long total = conn.getContentLengthLong(); // قد تكون -1
            bytesTotal.set(total);

            try (BufferedInputStream bis = new BufferedInputStream(conn.getInputStream())) {

                byte[] buffer = new byte[64 * 1024]; // 64KB
                long lastTime = System.nanoTime();
                long lastBytes = 0;

                long done = 0;
                while (true) {
                    int read = bis.read(buffer);
                    if (read == -1) break;

                    bos.write(buffer, 0, read);
                    done += read;
                    bytesDone.set(done);

                    // حدّث النسبة إن كان الإجمالي معروف
                    if (total > 0) {
                        int p = (int) Math.min(100, Math.round((done * 100.0) / total));
                        percent.set(p);
                    }

                    // قياس السرعة كل ~200ms
                    long now = System.nanoTime();
                    long dtNs = now - lastTime;
                    if (dtNs >= 200_000_000L) {
                        long dBytes = done - lastBytes;
                        double seconds = dtNs / 1_000_000_000.0;
                        double mbps = (dBytes / 1024.0 / 1024.0) / seconds; // MB/s اللحظية
                        // سلاسة بسيطة (moving average)
                        double smooth = 0.6 * speedMBps.get() + 0.4 * mbps;
                        speedMBps.set(smooth);

                        lastTime = now;
                        lastBytes = done;
                    }
                }
                bos.flush();

                // عند النهاية: اجعل النسبة 100% إذا كان الطول معروفاً
                if (total > 0) percent.set(100);

            }

            // نجاح
            javafx.application.Platform.runLater(this::completeDownload);

        } catch (Exception ex) {
            ex.printStackTrace();
            javafx.application.Platform.runLater(() -> {
                showMessage("Download failed: " + ex.getMessage(), true);
                cancelAndReset();
            });
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void completeDownload() {
        if (neonPulse != null) neonPulse.stop();
        if (dotsAnim != null)  dotsAnim.stop();

        // أخفِ خيار السماح بعد الاكتمال
        fadeOutAndRemove(allowAccess);

        // أخفِ سطر الحالة
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);

        morphToRect();
        state = State.COMPLETE;
        techButton.setCursor(Cursor.HAND);
        showMessage("Download completed successfully! Please click 'Next' to continue.", false);
    }

    private void cancelAndReset() {
        if (neonPulse != null) neonPulse.stop();
        if (dotsAnim != null)  dotsAnim.stop();

        state = State.IDLE;
        techButton.setCursor(Cursor.HAND);
        btnLabel.setText("Download App");
        btnLabel.setTextFill(Color.web("#E5E7EB"));

        // إعادة القيم
        bytesDone.set(0);
        percent.set(0);
        speedMBps.set(0.0);
        // أبقي progressLabel مخفيًا
        progressLabel.setVisible(false);
        progressLabel.setManaged(false);
    }

    /* ============== أنيميشن/تمركز ============== */

    private void morphToRect() {
        ParallelTransition fadeOut = new ParallelTransition(
                fade(core, 1, 0, 200),
                fade(neonRing, 1, 0, 200),
                fade(btnLabel, 1, 0, 200)
        );
        fadeOut.setOnFinished(ev -> {
            core.setVisible(false);   core.setManaged(false);
            neonRing.setVisible(false); neonRing.setManaged(false);
            btnLabel.setVisible(false); btnLabel.setManaged(false);
        });

        nextRect.setVisible(true);  nextRect.setManaged(true);
        nextLabel.setVisible(true); nextLabel.setManaged(true);

        double targetW = clamp(260, baseSize.get() * 1.5, 520);
        double targetH = clamp(56,  baseSize.get() * 0.30, 88);

        Timeline sizeAnim = new Timeline(
                new KeyFrame(Duration.ZERO,
                        new KeyValue(nextRect.widthProperty(), nextRect.getWidth(), Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.heightProperty(), nextRect.getHeight(), Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.arcWidthProperty(), nextRect.getArcWidth(), Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.arcHeightProperty(), nextRect.getArcHeight(), Interpolator.EASE_BOTH)),
                new KeyFrame(Duration.millis(320),
                        new KeyValue(nextRect.widthProperty(), targetW, Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.heightProperty(), targetH, Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.arcWidthProperty(), Math.min(24, targetH), Interpolator.EASE_BOTH),
                        new KeyValue(nextRect.arcHeightProperty(), Math.min(24, targetH), Interpolator.EASE_BOTH))
        );

        ParallelTransition fadeIn = new ParallelTransition(
                fade(nextRect, 0, 1, 240),
                fade(nextLabel, 0, 1, 240)
        );

        new SequentialTransition(fadeOut, sizeAnim, fadeIn).play();
    }

    private void resizeTech(double size) {
        double r = size / 2.0;

        baseCircle.setRadius(r);
        glassTop.setRadius(r * 0.86);
        glassTop.setTranslateY(-r * 0.18);

        neonRing.setRadius(r);

        btnLabel.setFont(Font.font("System", FontWeight.BOLD, clamp(18, size * 0.11, 28)));
        progressLabel.setFont(Font.font("System", FontWeight.SEMI_BOLD, clamp(11, size * 0.06, 14)));
        progressLabel.setTranslateY(Math.min(22, r * 0.28));

        nextLabel.setFont(Font.font("System", FontWeight.BOLD, clamp(16, size * 0.10, 24)));

        if (state == State.COMPLETE) {
            nextRect.setWidth(clamp(260, size * 1.5, 520));
            nextRect.setHeight(clamp(56, size * 0.30, 88));
            double arc = Math.min(24, nextRect.getHeight());
            nextRect.setArcWidth(arc);
            nextRect.setArcHeight(arc);
        }
    }

    private void showMessage(String text, boolean error) {
        messageBox.setText(text);
        if (error) styleError(messageBox); else styleInfo(messageBox);
        messageBox.setVisible(true);
        messageBox.setManaged(true);

        PauseTransition hide = new PauseTransition(Duration.seconds(3));
        hide.setOnFinished(e -> { messageBox.setVisible(false); messageBox.setManaged(false); });
        hide.playFromStart();
    }

    /* ================= Helpers ================= */

    // يحدّث سطر الحالة وفق القيم الحالية
    private void updateProgressLabel() {
        long total = bytesTotal.get();
        long done  = bytesDone.get();
        double speed = speedMBps.get();

        String percentTxt = (total > 0)
                ? (percent.get() + "% • ")
                : ""; // إن كان الطول غير معروف لا نعرض نسبة

        String sizeTxt = (total > 0)
                ? (formatMB(done) + " / " + formatMB(total) + " MB")
                : (formatMB(done) + " MB");

        String speedTxt = formatMB(speed) + " MB/s";

        String etaTxt;
        if (total > 0 && speed > 0.01) {
            double remainMB = Math.max(0.0, (total - done) / (1024.0 * 1024.0));
            double etaSec = remainMB / speed;
            etaTxt = etaSec < 0.5 ? "ETA <0.5s" : String.format(Locale.US, "ETA %.1fs", etaSec);
        } else {
            etaTxt = "ETA —";
        }

        progressLabel.setText(percentTxt + sizeTxt + " • " + speedTxt + " • " + etaTxt);
    }

    // يخفي العنصر بـ Fade ثم يلغي إدارته حتى لا يترك فراغ
    private void fadeOutAndRemove(javafx.scene.Node node) {
        if (node == null || !node.isManaged()) return;
        FadeTransition ft = new FadeTransition(Duration.millis(220), node);
        ft.setFromValue(1);
        ft.setToValue(0);
        ft.setOnFinished(e -> { node.setVisible(false); node.setManaged(false); });
        ft.play();
    }

    private void styleInfo(Label m) {
        m.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#0F172A55")),
                        new Stop(1, Color.web("#1F293755"))),
                new CornerRadii(10), Insets.EMPTY)));
        m.setTextFill(Color.web("#E2E8F0"));
        m.setBorder(new Border(new BorderStroke(Color.web("#22D3EE"),
                BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1))));
        m.setEffect(new InnerShadow(8, Color.rgb(34,211,238,0.25)));
    }

    private void styleError(Label m) {
        m.setBackground(new Background(new BackgroundFill(
                new LinearGradient(0,0,1,1,true, CycleMethod.NO_CYCLE,
                        new Stop(0, Color.web("#7F1D1D55")),
                        new Stop(1, Color.web("#991B1B55"))),
                new CornerRadii(10), Insets.EMPTY)));
        m.setTextFill(Color.web("#FECACA"));
        m.setBorder(new Border(new BorderStroke(Color.web("#F87171"),
                BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1))));
        m.setEffect(new InnerShadow(8, Color.rgb(248,113,113,0.25)));
    }

    private void pulse(javafx.scene.shape.Shape s) {
        ScaleTransition up = new ScaleTransition(Duration.millis(140), s);
        up.setToX(1.05); up.setToY(1.06);
        ScaleTransition down = new ScaleTransition(Duration.millis(180), s);
        down.setToX(1.0); down.setToY(1.0);
        new SequentialTransition(up, down).play();
    }

    private void shake(StackPane n) {
        TranslateTransition t1 = new TranslateTransition(Duration.millis(60), n); t1.setByX(-8);
        TranslateTransition t2 = new TranslateTransition(Duration.millis(60), n); t2.setByX(16);
        TranslateTransition t3 = new TranslateTransition(Duration.millis(60), n); t3.setByX(-12);
        TranslateTransition t4 = new TranslateTransition(Duration.millis(60), n); t4.setByX(4);
        SequentialTransition seq = new SequentialTransition(t1,t2,t3,t4);
        seq.setOnFinished(e -> n.setTranslateX(0));
        seq.play();
    }

    private FadeTransition fade(javafx.scene.Node n, double from, double to, int ms) {
        FadeTransition ft = new FadeTransition(Duration.millis(ms), n);
        ft.setFromValue(from); ft.setToValue(to);
        return ft;
    }

    private void scale(javafx.scene.Node n, double to, int ms) {
        ScaleTransition st = new ScaleTransition(Duration.millis(ms), n);
        st.setToX(to); st.setToY(to);
        st.play();
    }

    private double clamp(double min, double v, double max) {
        return Math.max(min, Math.min(max, v));
    }

    private String formatMB(double mb) {
        return String.format(Locale.US, "%.1f", mb);
    }
    private String formatMB(long bytes) {
        return String.format(Locale.US, "%.1f", bytes / (1024.0 * 1024.0));
    }

    public static void main(String[] args) {
        launch(args);
    }
}
