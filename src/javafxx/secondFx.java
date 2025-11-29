package javafxx;
//project
import javafx.animation.*;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.Glow;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.*;
import javafx.stage.Stage;
import javafx.util.Duration;

public class secondFx extends Application {

    private void fakeScript(Runnable doneAction) {
        new Thread(() -> {
            try { Thread.sleep(1500); } catch (Exception ignored) {}
            javafx.application.Platform.runLater(doneAction);
        }).start();
    }

    @Override
    public void start(Stage stage) {

        StackPane root = new StackPane();
        root.setStyle("-fx-background-color:#0c0c0c;");
        Pane layer = new Pane();
        root.getChildren().add(layer);

        /* ---------------- POWER MODE ---------------- */

        Label powerBtn = new Label("Power Mode ▼");
        powerBtn.setStyle(
                "-fx-text-fill:#9ff; -fx-font-size:22px; -fx-font-weight:bold;"
                        + "-fx-padding:10 20; -fx-background-color:#111;"
                        + "-fx-border-color:#00eaff; -fx-border-width:2;"
                        + "-fx-background-radius:10; -fx-border-radius:10;"
                        + "-fx-cursor:hand;"
        );
        powerBtn.setLayoutX(40);
        powerBtn.setLayoutY(40);

        VBox modeBox = new VBox(10);
        modeBox.setLayoutX(40);
        modeBox.setLayoutY(100);
        modeBox.setVisible(false);
        modeBox.setManaged(false);

        Label p1 = makeMode("Performance");
        Label p2 = makeMode("Balanced");
        Label p3 = makeMode("Efficiency");

        modeBox.getChildren().addAll(p1, p2, p3);

        powerBtn.setOnMouseClicked(e -> {
            boolean show = !modeBox.isVisible();
            modeBox.setVisible(show);
            modeBox.setManaged(show);
        });

        p1.setOnMouseClicked(e -> selectMode(powerBtn, modeBox, "Performance"));
        p2.setOnMouseClicked(e -> selectMode(powerBtn, modeBox, "Balanced"));
        p3.setOnMouseClicked(e -> selectMode(powerBtn, modeBox, "Efficiency"));

        layer.getChildren().addAll(powerBtn, modeBox);

        /* ---------------- ALL IN ONE CIRCLE ---------------- */

        Circle mainC = new Circle(120);
        mainC.setFill(Color.web("#1a1a1a"));
        mainC.setStroke(Color.web("#00eaff"));
        mainC.setStrokeWidth(4);

        Label mainTxt = new Label("ALL IN ONE");
        mainTxt.setStyle("-fx-text-fill:#dff; -fx-font-size:28px; -fx-font-weight:bold;");

        StackPane allPane = new StackPane(mainC, mainTxt);
        allPane.setPrefSize(260, 260);
        root.setAlignment(allPane, Pos.CENTER);
        layer.getChildren().add(allPane);

        addHoverFX(allPane);

        /* ---------------- BUTTONS ---------------- */

        Button cpuBtn = makeBtn("CPU");
        Button ramBtn = makeBtn("RAM & ROM");
        Button netBtn = makeBtn("Network");
        Button batBtn = makeBtn("Battery");

        addHoverFX(cpuBtn);
        addHoverFX(ramBtn);
        addHoverFX(netBtn);
        addHoverFX(batBtn);

        layer.getChildren().addAll(cpuBtn, ramBtn, netBtn, batBtn);

        /* ---------------- PATHS ---------------- */

        Path pCPU = makePath();
        Path pRAM = makePath();
        Path pNET = makePath();
        Path pBAT = makePath();

        layer.getChildren().addAll(pCPU, pRAM, pNET, pBAT);

        /* ---------------- DOTS ---------------- */

        Circle d1 = makeDot(), d2 = makeDot(), d3 = makeDot(), d4 = makeDot();
        layer.getChildren().addAll(d1, d2, d3, d4);

        /* ---------------- ACTION ---------------- */

        allPane.setOnMouseClicked(e -> {
            mainTxt.setText("Loading...");
            Glow gl = new Glow(0.0);
            allPane.setEffect(gl);

            Timeline g = new Timeline(
                    new KeyFrame(Duration.ZERO, new KeyValue(gl.levelProperty(), 0)),
                    new KeyFrame(Duration.seconds(1), new KeyValue(gl.levelProperty(), 0.8)),
                    new KeyFrame(Duration.seconds(2), new KeyValue(gl.levelProperty(), 0))
            );
            g.play();

            fakeScript(() -> cpuBtn.setText("Done ✔"));
            fakeScript(() -> ramBtn.setText("Done ✔"));
            fakeScript(() -> netBtn.setText("Done ✔"));
            fakeScript(() -> batBtn.setText("Done ✔"));

            new Thread(() -> {
                try { Thread.sleep(2000); } catch (Exception ignored) {}
                javafx.application.Platform.runLater(() -> mainTxt.setText("ALL IN ONE ✔"));
            }).start();
        });

        cpuBtn.setOnAction(e -> runSingle(cpuBtn));
        ramBtn.setOnAction(e -> runSingle(ramBtn));
        netBtn.setOnAction(e -> runSingle(netBtn));
        batBtn.setOnAction(e -> runSingle(batBtn));

        /* ---------------- RESIZE ---------------- */

        root.widthProperty().addListener((o, a, b) ->
                reposition(layer, allPane, cpuBtn, ramBtn, netBtn, batBtn,
                        pCPU, pRAM, pNET, pBAT, d1, d2, d3, d4));

        root.heightProperty().addListener((o, a, b) ->
                reposition(layer, allPane, cpuBtn, ramBtn, netBtn, batBtn,
                        pCPU, pRAM, pNET, pBAT, d1, d2, d3, d4));

        Scene scene = new Scene(root, 1400, 800);
        stage.setScene(scene);
        stage.setTitle("Ultra FX Panel – Final");
        stage.show();

        reposition(layer, allPane, cpuBtn, ramBtn, netBtn, batBtn,
                pCPU, pRAM, pNET, pBAT, d1, d2, d3, d4);
    }

    /* ---------------- HELPERS ---------------- */

    private Path makePath() {
        Path p = new Path();
        p.setStrokeWidth(2);
        p.setStroke(Color.web("#00eaff55"));
        return p;
    }

    private Label makeMode(String t) {
        Label l = new Label(t);
        l.setStyle(
                "-fx-text-fill:#aff; -fx-font-size:20px; -fx-padding:6 14;"
                        + "-fx-background-color:#111; -fx-border-color:#00eaff;"
                        + "-fx-border-width:2; -fx-background-radius:8; -fx-border-radius:8;"
        );
        l.setCursor(javafx.scene.Cursor.HAND);
        return l;
    }

    private Button makeBtn(String t) {
        Button b = new Button(t);
        b.setPrefSize(180, 60);
        b.setStyle(
                "-fx-background-color:#151515; -fx-text-fill:#ccffff;"
                        + "-fx-font-size:20px; -fx-background-radius:10;"
                        + "-fx-border-radius:10; -fx-border-color:#00eaff;"
                        + "-fx-border-width:2;"
        );
        return b;
    }

    private void selectMode(Label main, VBox box, String mode) {
        main.setText(mode + " ✔");
        box.setVisible(false); box.setManaged(false);
    }

    private void addHoverFX(Region n) {
        n.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), n);
            st.setToX(1.08); st.setToY(1.08);
            st.play();
            n.setEffect(new Glow(0.4));
        });

        n.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(150), n);
            st.setToX(1); st.setToY(1);
            st.play();
            n.setEffect(null);
        });
    }

    private Circle makeDot() {
        Circle c = new Circle(7, Color.web("#00ffff"));
        c.setEffect(new DropShadow(15, Color.web("#00ffffaa")));
        return c;
    }

    private void runSingle(Button b) {
        b.setText("Loading...");
        fakeScript(() -> b.setText("Done ✔"));
    }

    private void reposition(Pane layer, StackPane main,
                            Button cpu, Button ram, Button net, Button bat,
                            Path pCPU, Path pRAM, Path pNET, Path pBAT,
                            Circle d1, Circle d2, Circle d3, Circle d4) {

        double W = layer.getWidth();
        double H = layer.getHeight();

        double cx = W / 2;
        double cy = H / 2;

        main.setLayoutX(cx - main.getWidth() / 2);
        main.setLayoutY(cy - main.getHeight() / 2);

        /* --- aligned buttons --- */

        cpu.setLayoutX(cx + 320);
        cpu.setLayoutY(cy - 140);

        ram.setLayoutX(cx + 320);
        ram.setLayoutY(cy - 10);

        net.setLayoutX(cx - 500);
        net.setLayoutY(cy - 140);

        bat.setLayoutX(cx - 500);
        bat.setLayoutY(cy - 10);

        /* --- PATHS FIXED START/END POINTS --- */

        drawPath(pCPU, cpu, main, -120, false);
        drawPath(pRAM, ram, main, -60, false);
        drawPath(pNET, net, main, -120, true);
        drawPath(pBAT, bat, main, -60, true);

        animateDot(d1, pCPU);
        animateDot(d2, pRAM);
        animateDot(d3, pNET);
        animateDot(d4, pBAT);

        cpu.toFront();
        ram.toFront();
        net.toFront();
        bat.toFront();
    }

    private void drawPath(Path p, Button btn, StackPane center, double ctrl, boolean leftSide) {

        double startX = leftSide
                ? btn.getLayoutX() + btn.getWidth()
                : btn.getLayoutX();

        double startY = btn.getLayoutY() + btn.getHeight() / 2;

        double endX = center.getLayoutX() + center.getWidth() / 2;
        double endY = center.getLayoutY() + center.getHeight() / 2;

        p.getElements().setAll(
                new MoveTo(startX, startY),
                new QuadCurveTo(
                        (startX + endX) / 2,
                        (startY + endY) / 2 + ctrl,
                        endX, endY
                )
        );
        p.toBack();
    }

    private void animateDot(Circle c, Path p) {
        PathTransition pt = new PathTransition(Duration.seconds(3), p, c);
        pt.setCycleCount(Animation.INDEFINITE);
        pt.play();
    }

    public static void main(String[] args) {
        launch();
    }
}
