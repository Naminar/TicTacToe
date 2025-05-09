import javafx.application.Application;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;

import java.util.HashMap;
import java.util.Map;

public class InfiniteTicTacToe extends Application {

    private enum Player { X, O }

    private static final int WIN_LENGTH = 5;

    private double CELL_SIZE = 40.0;


    private static final double ZOOM_FACTOR = 1.1;
    private static final double MIN_CELL_SIZE = 10.0;
    private static final double MAX_CELL_SIZE = 200.0;


    private Map<String, Player> boardData = new HashMap<>();
    private Canvas canvas;
    private GraphicsContext gc;

    private double viewLogicalX = 0;
    private double viewLogicalY = 0;

    private Player currentPlayer = Player.X;
    private boolean gameOver = false;
    private Label statusLabel;

    private Point2D lastMousePressCanvasPosition;
    private Point2D viewOriginAtMousePress;
    private boolean isDragging = false;
    private static final double DRAG_THRESHOLD = 5;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        BorderPane rootLayout = new BorderPane();

        statusLabel = new Label();
        statusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        Button resetButton = new Button("Новая игра");
        resetButton.setOnAction(e -> resetGame());
        HBox topPanel = new HBox(20, statusLabel, resetButton);
        topPanel.setAlignment(Pos.CENTER);
        topPanel.setPadding(new javafx.geometry.Insets(10));
        rootLayout.setTop(topPanel);

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        BorderPane canvasContainer = new BorderPane(canvas);
        rootLayout.setCenter(canvasContainer);

        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        canvas.widthProperty().addListener(observable -> redrawCanvas());
        canvas.heightProperty().addListener(observable -> redrawCanvas());

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);

        canvas.setOnScroll(this::handleMouseScroll);

        Scene scene = new Scene(rootLayout, 800, 600);
        primaryStage.setTitle("Крестики-нолики на бесконечном поле (Canvas + Zoom)");
        primaryStage.setScene(scene);
        primaryStage.show();

        resetGame();
    }

    private void handleMousePressed(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            lastMousePressCanvasPosition = new Point2D(event.getX(), event.getY());
            viewOriginAtMousePress = new Point2D(viewLogicalX, viewLogicalY);
            isDragging = false;
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY && lastMousePressCanvasPosition != null) {
            double deltaXSincePress = event.getX() - lastMousePressCanvasPosition.getX();
            double deltaYSincePress = event.getY() - lastMousePressCanvasPosition.getY();

            if (!isDragging) {
                if (Math.sqrt(deltaXSincePress * deltaXSincePress + deltaYSincePress * deltaYSincePress) > DRAG_THRESHOLD) {
                    isDragging = true;
                    canvas.setCursor(Cursor.MOVE);
                }
            }

            if (isDragging) {

                viewLogicalX = viewOriginAtMousePress.getX() - deltaXSincePress / CELL_SIZE;
                viewLogicalY = viewOriginAtMousePress.getY() - deltaYSincePress / CELL_SIZE;
                redrawCanvas();
            }
        }
    }

    private void handleMouseReleased(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            if (!isDragging) {
                handleBoardClick(event);
            }
            canvas.setCursor(Cursor.DEFAULT);
            isDragging = false;
            lastMousePressCanvasPosition = null;
            viewOriginAtMousePress = null;
        }
    }

    private void handleMouseScroll(ScrollEvent event) {
        if (event.isControlDown()) {
            double oldCellSize = CELL_SIZE;
            double mouseCanvasX = event.getX();
            double mouseCanvasY = event.getY();


            double logicalMouseColBeforeZoom = viewLogicalX + mouseCanvasX / oldCellSize;
            double logicalMouseRowBeforeZoom = viewLogicalY + mouseCanvasY / oldCellSize;

            if (event.getDeltaY() > 0) {
                CELL_SIZE *= ZOOM_FACTOR;
            } else if (event.getDeltaY() < 0) {
                CELL_SIZE /= ZOOM_FACTOR;
            }


            CELL_SIZE = Math.max(MIN_CELL_SIZE, Math.min(MAX_CELL_SIZE, CELL_SIZE));



            viewLogicalX = logicalMouseColBeforeZoom - mouseCanvasX / CELL_SIZE;
            viewLogicalY = logicalMouseRowBeforeZoom - mouseCanvasY / CELL_SIZE;

            redrawCanvas();
            event.consume();
        }



    }


    private void handleBoardClick(MouseEvent event) {
        if (gameOver) return;

        double canvasX = event.getX();
        double canvasY = event.getY();


        int logicalCol = (int) Math.floor(viewLogicalX + canvasX / CELL_SIZE);
        int logicalRow = (int) Math.floor(viewLogicalY + canvasY / CELL_SIZE);

        String cellKey = logicalRow + "," + logicalCol;

        if (!boardData.containsKey(cellKey)) {
            boardData.put(cellKey, currentPlayer);
            redrawCanvas();

            if (checkWin(logicalRow, logicalCol, currentPlayer)) {
                gameOver = true;
                statusLabel.setText("Игрок " + currentPlayer + " выиграл!");
                showAlert("Игра окончена", "Игрок " + currentPlayer + " выиграл!");
            } else {
                currentPlayer = (currentPlayer == Player.X) ? Player.O : Player.X;
                updateStatusLabel();
            }
        }
    }

    private void redrawCanvas() {
        if (canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;

        gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
        gc.setFill(Color.WHITE);
        gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());

        gc.setStroke(Color.BLACK);

        gc.setLineWidth(Math.max(0.3, Math.min(1.5, CELL_SIZE / 80.0)));



        double startLogicalCol = viewLogicalX;
        double endLogicalCol = viewLogicalX + canvas.getWidth() / CELL_SIZE;
        double startLogicalRow = viewLogicalY;
        double endLogicalRow = viewLogicalY + canvas.getHeight() / CELL_SIZE;

        double firstVerticalLineCanvasX = (Math.ceil(startLogicalCol) - startLogicalCol) * CELL_SIZE;
        for (double canvasX = firstVerticalLineCanvasX; canvasX < canvas.getWidth(); canvasX += CELL_SIZE) {
            gc.strokeLine(Math.round(canvasX), 0, Math.round(canvasX), canvas.getHeight());
        }

        double firstHorizontalLineCanvasY = (Math.ceil(startLogicalRow) - startLogicalRow) * CELL_SIZE;
        for (double canvasY = firstHorizontalLineCanvasY; canvasY < canvas.getHeight(); canvasY += CELL_SIZE) {
            gc.strokeLine(0, Math.round(canvasY), canvas.getWidth(), Math.round(canvasY));
        }

        gc.setFont(Font.font("Arial", FontWeight.BOLD, CELL_SIZE * 0.7));
        gc.setTextAlign(TextAlignment.CENTER);

        int iterStartRow = (int) Math.floor(startLogicalRow);
        int iterEndRow = (int) Math.ceil(endLogicalRow);
        int iterStartCol = (int) Math.floor(startLogicalCol);
        int iterEndCol = (int) Math.ceil(endLogicalCol);

        for (int r = iterStartRow; r < iterEndRow; r++) {
            for (int c = iterStartCol; c < iterEndCol; c++) {
                String key = r + "," + c;
                if (boardData.containsKey(key)) {
                    Player player = boardData.get(key);
                    gc.setFill(player == Player.X ? Color.DODGERBLUE : Color.CRIMSON);

                    double cellCenterCanvasX = (c - viewLogicalX + 0.5) * CELL_SIZE;
                    double cellCenterCanvasY = (r - viewLogicalY + 0.5) * CELL_SIZE;

                    gc.fillText(player.toString(),
                                Math.round(cellCenterCanvasX),
                                Math.round(cellCenterCanvasY + CELL_SIZE * 0.25));
                }
            }
        }
    }

    private String getPlayerSymbolAt(int logicalRow, int logicalCol) {
        Player p = boardData.get(logicalRow + "," + logicalCol);
        return (p != null) ? p.toString() : null;
    }

    private boolean checkWin(int lastMoveRow, int lastMoveCol, Player player) {
        String playerSymbol = player.toString();
        if (countConsecutive(lastMoveRow, lastMoveCol, playerSymbol, 0, 1) >= WIN_LENGTH) return true;
        if (countConsecutive(lastMoveRow, lastMoveCol, playerSymbol, 1, 0) >= WIN_LENGTH) return true;
        return false;
    }

    private int countConsecutive(int r, int c, String playerSymbol, int dr, int dc) {
        int count = 1;
        for (int i = 1; i < WIN_LENGTH; i++) {
            if (playerSymbol.equals(getPlayerSymbolAt(r + i * dr, c + i * dc))) count++;
            else break;
        }
        for (int i = 1; i < WIN_LENGTH; i++) {
            if (playerSymbol.equals(getPlayerSymbolAt(r - i * dr, c - i * dc))) count++;
            else break;
        }
        return count;
    }

    private void updateStatusLabel() {
        if (gameOver) {

        } else {
            statusLabel.setText("Ход игрока: " + currentPlayer);
        }
    }

    private void resetGame() {
        boardData.clear();
        currentPlayer = Player.X;
        gameOver = false;
        updateStatusLabel();




        if (canvas != null && canvas.getWidth() > 0 && canvas.getHeight() > 0) {
             viewLogicalX = - (canvas.getWidth() / CELL_SIZE) / 2.0;
             viewLogicalY = - (canvas.getHeight() / CELL_SIZE) / 2.0;
        } else {
            viewLogicalX = -10;
            viewLogicalY = -7;
        }
        redrawCanvas();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}