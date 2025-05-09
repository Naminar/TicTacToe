import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.ScaleTransition;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.ScrollEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.TextAlignment;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class InfiniteTicTacToeWithMenuImgButt extends Application {

    private enum Player { X, O }


    private static final int WIN_LENGTH = 5;
    private double CELL_SIZE = 40.0;

    private static final double ZOOM_FACTOR = 1.1;
    private static final double MIN_CELL_SIZE = 10.0;
    private static final double MAX_CELL_SIZE = 150.0;

    private Map<String, Player> boardData = new HashMap<>();
    private Player currentPlayer = Player.X;
    private boolean gameOver = false;


    private Canvas canvas;
    private GraphicsContext gc;
    private double viewLogicalX = -10;
    private double viewLogicalY = -7;


    private Point2D lastMousePressCanvasPosition;
    private Point2D viewOriginAtMousePress;
    private boolean isDragging = false;
    private static final double DRAG_THRESHOLD = 5;


    private Stage primaryStage;
    private Scene mainMenuScene;
    private Scene gameScene;
    private Scene stylesScene;
    private Label gameStatusLabel;


    private static class ColorScheme {
        final String name;
        final Color colorForX;
        final Color colorForO;

        ColorScheme(String name, Color xParamColor, Color oParamColor) {
            this.name = name;
            this.colorForX = xParamColor;
            this.colorForO = oParamColor;
        }
    }

    private final List<ColorScheme> availableColorSchemes = List.of(
            new ColorScheme("Классика (Синий/Красный)", Color.DODGERBLUE, Color.CRIMSON),
            new ColorScheme("Зеленый/Оранжевый", Color.FORESTGREEN, Color.DARKORANGE),
            new ColorScheme("Фиолетовый/Золотой", Color.MEDIUMPURPLE, Color.GOLD),
            new ColorScheme("Черно-белый", Color.BLACK, Color.DIMGRAY)
    );
    private Color currentColorX = availableColorSchemes.get(0).colorForX;
    private Color currentColorO = availableColorSchemes.get(0).colorForO;


    private Pane animationPane;
    private Timeline backgroundAnimationTimeline;
    private final Random random = new Random();
    private final List<Color> animationColors = List.of(
            Color.LIGHTBLUE, Color.LIGHTCORAL, Color.LIGHTGREEN, Color.LIGHTPINK,
            Color.LIGHTSALMON, Color.LIGHTSEAGREEN, Color.LIGHTSKYBLUE, Color.PALEVIOLETRED
    );



    private Image playButtonNormalImage, playButtonHoverImage;
    private Image stylesButtonNormalImage, stylesButtonHoverImage;
    private Image exitButtonNormalImage, exitButtonHoverImage;


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;

        loadButtonImages();

        this.mainMenuScene = createMainMenuScene();
        this.stylesScene = createStylesScene();
        this.gameScene = createGameScene();

        resetGameLogic();

        primaryStage.setTitle("Крестики-нолики: Главное Меню");
        primaryStage.setScene(mainMenuScene);
        primaryStage.show();

        primaryStage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == mainMenuScene) {
                startBackgroundAnimation();
            } else {
                stopBackgroundAnimation();
            }
        });
        startBackgroundAnimation();
    }

    private void loadButtonImages() {


        playButtonNormalImage = loadImage("/img/button_play_normal.png");
        playButtonHoverImage = loadImage("/img/button_play_hover.png");

        stylesButtonNormalImage = loadImage("/img/button_styles_normal.png");
        stylesButtonHoverImage = loadImage("/img/button_styles_hover.png");

        exitButtonNormalImage = loadImage("/img/button_exit_normal.png");
        exitButtonHoverImage = loadImage("/img/button_exit_hover.png");


        if (playButtonNormalImage == null || stylesButtonNormalImage == null || exitButtonNormalImage == null ||
            playButtonHoverImage == null || stylesButtonHoverImage == null || exitButtonHoverImage == null) {
            System.err.println("ВНИМАНИЕ: Не все изображения для кнопок были загружены. Кнопки могут отображаться некорректно.");



            if (playButtonHoverImage == null) playButtonHoverImage = playButtonNormalImage;
            if (stylesButtonHoverImage == null) stylesButtonHoverImage = stylesButtonNormalImage;
            if (exitButtonHoverImage == null) exitButtonHoverImage = exitButtonNormalImage;
        }
    }

    private Image loadImage(String path) {
        try {
            InputStream stream = getClass().getResourceAsStream(path);
            if (stream == null) {
                System.err.println("Не удалось загрузить изображение: " + path);
                return null;
            }
            return new Image(stream);
        } catch (Exception e) {
            System.err.println("Ошибка при загрузке изображения " + path + ": " + e.getMessage());
            return null;
        }
    }


    private Node createImageOnlyButton(Image normalImage, Image hoverImage, double width, double height, Runnable action) {

        if (normalImage == null) {
            Button fallbackButton = new Button("Ошибка загрузки");
            fallbackButton.setPrefSize(width, height);
            fallbackButton.setOnAction(e -> action.run());
            System.err.println("Используется кнопка-заглушка, т.к. основное изображение не загружено.");
            return fallbackButton;
        }

        final Image finalHoverImage = (hoverImage != null) ? hoverImage : normalImage;


        ImageView imageView = new ImageView(normalImage);
        imageView.setFitWidth(width);
        imageView.setFitHeight(height);
        imageView.setPreserveRatio(false);


        StackPane buttonPane = new StackPane(imageView);
        buttonPane.setPrefSize(width, height);
        buttonPane.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        buttonPane.setAlignment(Pos.CENTER);



        final double scaleFactor = 1.05;
        ScaleTransition stIn = new ScaleTransition(Duration.millis(100), buttonPane);
        stIn.setToX(scaleFactor);
        stIn.setToY(scaleFactor);

        ScaleTransition stOut = new ScaleTransition(Duration.millis(100), buttonPane);
        stOut.setToX(1.0);
        stOut.setToY(1.0);

        buttonPane.setOnMouseEntered(e -> {
            imageView.setImage(finalHoverImage);
            stIn.playFromStart();
            buttonPane.setCursor(Cursor.HAND);
        });

        buttonPane.setOnMouseExited(e -> {
            imageView.setImage(normalImage);
            stOut.playFromStart();
            buttonPane.setCursor(Cursor.DEFAULT);
        });

        buttonPane.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {

                ScaleTransition pressDown = new ScaleTransition(Duration.millis(70), buttonPane);
                pressDown.setToX(scaleFactor * 0.95);
                pressDown.setToY(scaleFactor * 0.95);

                ScaleTransition pressUp = new ScaleTransition(Duration.millis(70), buttonPane);
                pressUp.setToX(scaleFactor);
                pressUp.setToY(scaleFactor);

                pressDown.setOnFinished(event -> pressUp.play());
                pressUp.setOnFinished(event -> action.run());
                pressDown.play();
            }
        });

        buttonPane.setOnMouseReleased(e -> {
             if (imageView.getImage() == finalHoverImage) {

                buttonPane.setScaleX(scaleFactor);
                buttonPane.setScaleY(scaleFactor);
            } else {
                imageView.setImage(normalImage);
                buttonPane.setScaleX(1.0);
                buttonPane.setScaleY(1.0);
            }
        });


        return buttonPane;
    }


    private Scene createMainMenuScene() {
        Label titleLabel = new Label("Крестики-нолики на Бесконечном Поле");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);


        Node playButton = createImageOnlyButton(playButtonNormalImage, playButtonHoverImage, 220, 70, () -> {
            primaryStage.setScene(gameScene);
            primaryStage.setTitle("Крестики-нолики");
            updateGameStatusLabel();
            redrawCanvas();
        });

        Node stylesButton = createImageOnlyButton(stylesButtonNormalImage, stylesButtonHoverImage, 220, 70, () -> {
            primaryStage.setScene(stylesScene);
            primaryStage.setTitle("Настройка Стилей");
        });

        Node exitButton = createImageOnlyButton(exitButtonNormalImage, exitButtonHoverImage, 220, 70, Platform::exit);


        VBox menuButtonsLayout = new VBox(25, titleLabel, playButton, stylesButton, exitButton);
        menuButtonsLayout.setAlignment(Pos.CENTER);
        menuButtonsLayout.setPadding(new Insets(50));

        animationPane = new Pane();
        animationPane.setMouseTransparent(true);

        StackPane stackPane = new StackPane();
        stackPane.getChildren().addAll(animationPane, menuButtonsLayout);

        Stop[] stops = new Stop[]{
                new Stop(0, Color.web("#521d35")),
                new Stop(0.5, Color.web("#74460b")),
                new Stop(1, Color.web("#dbad73"))
        };
        LinearGradient lg = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, stops);
        stackPane.setBackground(new Background(new BackgroundFill(lg, CornerRadii.EMPTY, Insets.EMPTY)));

        return new Scene(stackPane, 800, 600);
    }


        private void startBackgroundAnimation() {
        if (animationPane == null) return;
        if (backgroundAnimationTimeline != null) {
            backgroundAnimationTimeline.stop();
        }
        backgroundAnimationTimeline = new Timeline(new KeyFrame(Duration.seconds(1.5), event -> {
            if (primaryStage.getScene() != mainMenuScene || animationPane.getScene() == null) {
                stopBackgroundAnimation();
                return;
            }
            createAnimatedSymbol();
        }));
        backgroundAnimationTimeline.setCycleCount(Timeline.INDEFINITE);
        backgroundAnimationTimeline.play();
    }

    private void stopBackgroundAnimation() {
        if (backgroundAnimationTimeline != null) {
            backgroundAnimationTimeline.stop();
            backgroundAnimationTimeline = null;
        }
        if (animationPane != null) {
            animationPane.getChildren().clear();
        }
    }

    private void createAnimatedSymbol() {
        if (animationPane.getWidth() <= 0 || animationPane.getHeight() <= 0) return;

        String symbolText = random.nextBoolean() ? "X" : "O";
        Label symbolLabel = new Label(symbolText);

        double size = 30 + random.nextInt(70);
        symbolLabel.setFont(Font.font("Arial", FontWeight.BOLD, size));
        symbolLabel.setTextFill(animationColors.get(random.nextInt(animationColors.size())));
        symbolLabel.setOpacity(0);

        double x = random.nextDouble() * (animationPane.getWidth() - size);
        double y = random.nextDouble() * (animationPane.getHeight() - size);
        symbolLabel.setLayoutX(x);
        symbolLabel.setLayoutY(y);

        animationPane.getChildren().add(symbolLabel);

        FadeTransition fadeIn = new FadeTransition(Duration.seconds(0.7), symbolLabel);
        fadeIn.setToValue(0.7);

        FadeTransition fadeOut = new FadeTransition(Duration.seconds(1.5), symbolLabel);
        fadeOut.setDelay(Duration.seconds(1.0));
        fadeOut.setToValue(0);

        fadeIn.setOnFinished(e -> fadeOut.play());
        fadeOut.setOnFinished(e -> animationPane.getChildren().remove(symbolLabel));

        fadeIn.play();
    }


    private Scene createStylesScene() {
        Label titleLabel = new Label("Выберите Цветовую Схему");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 20));

        VBox schemesLayout = new VBox(15);
        schemesLayout.setAlignment(Pos.CENTER_LEFT);
        schemesLayout.setPadding(new Insets(20));

        ToggleGroup group = new ToggleGroup();

        for (ColorScheme scheme : availableColorSchemes) {
            RadioButton rb = new RadioButton(scheme.name);
            rb.setUserData(scheme);
            rb.setToggleGroup(group);
            rb.setFont(Font.font(14));

            HBox colorSampleBox = new HBox(10);
            colorSampleBox.setAlignment(Pos.CENTER_LEFT);
            Label xText = new Label("X:");
            xText.setFont(Font.font(14));
            Rectangle xRect = new Rectangle(20, 20, scheme.colorForX);
            xRect.setStroke(Color.BLACK);
            Label oText = new Label("O:");
            oText.setFont(Font.font(14));
            Rectangle oRect = new Rectangle(20, 20, scheme.colorForO);
            oRect.setStroke(Color.BLACK);
            colorSampleBox.getChildren().addAll(xText, xRect, new Label("  "), oText, oRect);

            HBox line = new HBox(20, rb, colorSampleBox);
            line.setAlignment(Pos.CENTER_LEFT);
            schemesLayout.getChildren().add(line);

            if (scheme.colorForX.equals(currentColorX) && scheme.colorForO.equals(currentColorO)) {
                rb.setSelected(true);
            }
        }

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                ColorScheme selected = (ColorScheme) newToggle.getUserData();
                currentColorX = selected.colorForX;
                currentColorO = selected.colorForO;
                if (primaryStage.getScene() == gameScene) {
                    redrawCanvas();
                }
            }
        });

        Button backButton = new Button("Назад в Меню"); 
        backButton.setOnAction(e -> primaryStage.setScene(mainMenuScene));

        VBox mainLayout = new VBox(30, titleLabel, schemesLayout, backButton);
        mainLayout.setAlignment(Pos.CENTER);
        mainLayout.setPadding(new Insets(30));

        return new Scene(mainLayout, 800, 600);
    }

    private Scene createGameScene() {
        BorderPane gameRootLayout = new BorderPane();

        gameStatusLabel = new Label();
        gameStatusLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));

        Button newGameInGameButton = new Button("Новая игра"); 
        newGameInGameButton.setOnAction(e -> {
            resetGameLogic();
            redrawCanvas();
        });

        Button backToMenuButton = new Button("В Меню"); 
        backToMenuButton.setOnAction(e -> {
            primaryStage.setScene(mainMenuScene);
            primaryStage.setTitle("Крестики-нолики: Главное Меню");
        });

        HBox gameTopPanel = new HBox(20, gameStatusLabel, newGameInGameButton, backToMenuButton);
        gameTopPanel.setAlignment(Pos.CENTER);
        gameTopPanel.setPadding(new Insets(10));
        gameRootLayout.setTop(gameTopPanel);

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        BorderPane canvasContainer = new BorderPane(canvas);
        gameRootLayout.setCenter(canvasContainer);

        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());

        canvas.widthProperty().addListener(observable -> redrawCanvas());
        canvas.heightProperty().addListener(observable -> redrawCanvas());

        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnScroll(this::handleMouseScroll);

        return new Scene(gameRootLayout, 800, 600);
    }



    private void resetGameLogic() {
        boardData.clear();
        currentPlayer = Player.X;
        gameOver = false;
        updateGameStatusLabel();

        CELL_SIZE = 40.0;
        if (canvas != null && canvas.getWidth() > 0 && canvas.getHeight() > 0) {
            viewLogicalX = -(canvas.getWidth() / CELL_SIZE) / 2.0;
            viewLogicalY = -(canvas.getHeight() / CELL_SIZE) / 2.0;
        } else {
            viewLogicalX = -10;
            viewLogicalY = -7;
        }
    }

    private void updateGameStatusLabel() {
        if (gameStatusLabel == null) return;
        if (gameOver) {

        } else {
            gameStatusLabel.setText("Ход игрока: " + currentPlayer);
        }
    }

    private void handleMousePressed(MouseEvent event) {

        if (primaryStage.getScene() != gameScene) return;

        if (event.getButton() == MouseButton.PRIMARY) {
            lastMousePressCanvasPosition = new Point2D(event.getX(), event.getY());
            viewOriginAtMousePress = new Point2D(viewLogicalX, viewLogicalY);
            isDragging = false;
        }
    }

    private void handleMouseDragged(MouseEvent event) {
        if (primaryStage.getScene() != gameScene) return;

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
        if (primaryStage.getScene() != gameScene) return;

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
        if (primaryStage.getScene() != gameScene) return;

        if (event.isControlDown()) {
            double oldCellSize = CELL_SIZE;
            double mouseCanvasX = event.getX();
            double mouseCanvasY = event.getY();

            double logicalMouseColBeforeZoom = viewLogicalX + mouseCanvasX / oldCellSize;
            double logicalMouseRowBeforeZoom = viewLogicalY + mouseCanvasY / oldCellSize;

            if (event.getDeltaY() > 0) CELL_SIZE *= ZOOM_FACTOR;
            else if (event.getDeltaY() < 0) CELL_SIZE /= ZOOM_FACTOR;

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

            if (checkWin(logicalRow, logicalCol, currentPlayer)) {
                gameOver = true;
                gameStatusLabel.setText("Игрок " + currentPlayer + " выиграл!");
                showAlert("Игра окончена", "Игрок " + currentPlayer + " выиграл!");
            } else {
                currentPlayer = (currentPlayer == Player.X) ? Player.O : Player.X;
                updateGameStatusLabel();
            }
            redrawCanvas();
        }
    }

    private void redrawCanvas() {
        if (gc == null || canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;

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
                    gc.setFill(player == Player.X ? currentColorX : currentColorO);

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
            if (playerSymbol.equals(getPlayerSymbolAt(r + i * dr, c + i * dc))) count++; else break;
        }
        for (int i = 1; i < WIN_LENGTH; i++) {
            if (playerSymbol.equals(getPlayerSymbolAt(r - i * dr, c - i * dc))) count++; else break;
        }
        return count;
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}