package client;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class GUIFrontendStarter extends Application {
    public static void main(String[] args) {
        launch(args);
    }

    public void start(Stage stage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("GUI.fxml"));
        Parent root = loader.load();
        Scene scene = new Scene(root);

        stage.setTitle("UDP Chat");
        stage.setScene(scene);
        stage.show();
        ((GUIFrontend)loader.getController()).start();
    }
}