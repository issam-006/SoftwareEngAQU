/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/javafx/FXMain.java to edit this template
 */
package javafxx;

import javafx.application.Application;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

/**
 *
 * @author essam
 */
import javafx.application.Application;
import javafx.stage.Stage;
import java.io.File;

public class MainLauncher extends Application {

    @Override
    public void start(Stage stage) throws Exception {

        File flag = new File("first_run.flag");

        if (!flag.exists()) {
            flag.createNewFile();   // أول تشغيل → ننشئ الفلاج
            new JavaFXApplication5().start(new Stage());
        } else {
            new secondFx().start(new Stage());  // تشغيل لاحق → secondFx فقط
        }
    }

    public static void main(String[] args) {
        launch();
    }
}

