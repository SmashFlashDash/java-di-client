package appexamples;

import java.util.Timer;
import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

/**
 *
 * @web http://java-buddy.blogspot.com/
 */
public class JavaFX_TimerTask extends Application {

    final int MAX = 100;

    Thread myTaskThread;
    Thread myRunnableThread;
    Timer myTimer;

    MyTask myTask;
    MyRunnable myRunnable;
    MyTimerTask myTimerTask;

    @Override
    public void start(Stage primaryStage) {

        myTask = new MyTask();
        ProgressBar progressBarTask = new ProgressBar();
        progressBarTask.setProgress(0);
        progressBarTask.progressProperty().bind(myTask.progressProperty());

        ProgressBar progressBarRunnable = new ProgressBar();
        progressBarRunnable.setProgress(0);
        myRunnable = new MyRunnable(progressBarRunnable);

        ProgressBar progressBarTimerTask = new ProgressBar();
        progressBarTimerTask.setProgress(0);
        myTimerTask = new MyTimerTask(progressBarTimerTask);

        Button btnStart = new Button("Start Task");
        btnStart.setOnAction(new EventHandler<ActionEvent>() {

            @Override
            public void handle(ActionEvent t) {
                myTaskThread = new Thread(myTask);
                myTaskThread.start();

                myRunnableThread = new Thread(myRunnable);
                myRunnableThread.start();

                myTimer = new Timer();
                myTimer.scheduleAtFixedRate(myTimerTask, 0, 100);
            }
        });

        VBox vBox = new VBox();
        vBox.setPadding(new Insets(5, 5, 5, 5));
        vBox.setSpacing(5);
        vBox.getChildren().addAll(
                new Label("Run in Thread(Task)"),
                progressBarTask,
                new Label("Run in Thread(Runnable)"),
                progressBarRunnable,
                new Label("Run in Timer and TimerTask"),
                progressBarTimerTask,
                btnStart);

        StackPane root = new StackPane();
        root.getChildren().add(vBox);

        Scene scene = new Scene(root, 300, 250);

        primaryStage.setTitle("java-buddy.blogspot.com");
        primaryStage.setScene(scene);
        primaryStage.show();

    }

    public static void main(String[] args) {
        Application.launch(args);
    }

    class MyTask extends Task<Void>{

        @Override
        protected Void call() throws Exception {
            for (int i = 1; i <= MAX; i++) {
                updateProgress(i, MAX);
                Thread.sleep(100);
            }
            return null;
        }

    }

    class MyRunnable implements Runnable{

        ProgressBar bar;

        public MyRunnable(ProgressBar b) {
            bar = b;
        }

        @Override
        public void run() {
            for (int i = 1; i <= MAX; i++) {

                final double update_i = i;

                //Not work if update JavaFX UI here!
                //bar.setProgress(i/MAX);

                //Update JavaFX UI with runLater() in UI thread
                Platform.runLater(new Runnable(){

                    @Override
                    public void run() {
                        bar.setProgress(update_i/MAX);
                    }
                });

                try {
                    Thread.sleep(100);
                } catch (InterruptedException ex) {
                    Logger.getLogger(JavaFX_TimerTask.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }

    }

    class MyTimerTask extends TimerTask{

        ProgressBar bar;
        double count;

        public MyTimerTask(ProgressBar b) {
            bar = b;
            count = 0;
        }

        @Override
        public void run() {

            bar.setProgress(count++/MAX);

            if(count >= MAX){
                myTimer.cancel();
            }

        }

    }

}