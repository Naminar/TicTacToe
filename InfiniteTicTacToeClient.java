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
import javafx.scene.control.*;
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
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.*;
import java.net.Socket;
import java.net.SocketException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Properties;
import java.util.stream.Collectors;

public class InfiniteTicTacToeClient extends Application {

    private enum Player {
        X, O;

        public Player opposite() {
            return this == X ? O : X;
        }
    }


    private static final int WIN_LENGTH = 5;
    private double CELL_SIZE = 40.0;
    private static final double ZOOM_FACTOR = 1.1;
    private static final double MIN_CELL_SIZE = 10.0;
    private static final double MAX_CELL_SIZE = 150.0;

    private Map<String, Player> boardData = new HashMap<>();
    private Player currentPlayerForLocalGame = Player.X;
    private boolean gameOver = false;
    private Player myPlayerSymbol = null;
    private boolean myTurn = false;
    private Player currentTurnPlayerNetwork = null;
    private String opponentPlayerName = "?";



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
    private Scene networkConfigScene;
    private Text statusTextNode;
    private TextArea chatArea;
    private TextField chatInput;
    private Label opponentNameLabel;


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
            new ColorScheme("Черно-белый", Color.BLACK, Color.DIMGRAY),
            new ColorScheme("Морской бриз (Голубой/Бирюзовый)", Color.SKYBLUE, Color.TURQUOISE),
            new ColorScheme("Огонь и Лед (Оранжево-Красный/Ледяной Голубой)", Color.ORANGERED, Color.LIGHTCYAN),
            new ColorScheme("Пастель (Розовый/Мятный)", Color.LIGHTPINK, Color.PALEGREEN),
            new ColorScheme("Неон (Ярко-Розовый/Лайм)", Color.DEEPPINK, Color.LIMEGREEN)
    );
    private int currentColorSchemeIndex = 0;
    private Color currentColorX = availableColorSchemes.get(0).colorForX;
    private Color currentColorO = availableColorSchemes.get(0).colorForO;


    private Pane animationPane;
    private Timeline backgroundAnimationTimeline;
    private final Random random = new Random();
    private final List<Color> animationColors = List.of(
            Color.LIGHTBLUE, Color.LIGHTCORAL, Color.LIGHTGREEN, Color.LIGHTPINK,
            Color.LIGHTSALMON, Color.LIGHTSEAGREEN, Color.LIGHTSKYBLUE, Color.PALEVIOLETRED
    );
    private static final int MAX_ANIMATED_SYMBOLS = 8;
    private static final double MIN_ANIM_DURATION_SECONDS = 1.5;
    private static final double MAX_ANIM_DURATION_SECONDS = 5.5;
    private static final double FADE_IN_OUT_DURATION_SECONDS = 0.7;


    private Image playButtonNormalImage, playButtonHoverImage;
    private Image stylesButtonNormalImage, stylesButtonHoverImage;
    private Image exitButtonNormalImage, exitButtonHoverImage;
    private Image backButtonNormalImage, backButtonHoverImage;
    private Image newGameIngameButtonNormalImage, newGameIngameButtonHoverImage;
    private Image backToMenuIngameButtonNormalImage, backToMenuIngameButtonHoverImage;
    private Image saveGameButtonNormalImage, saveGameButtonHoverImage;
    private Image loadGameButtonNormalImage, loadGameButtonHoverImage;
    private Image networkGameButtonNormalImage, networkGameButtonHoverImage;

    private static final String DEFAULT_SAVE_FILENAME = "tictactoe_save.properties";
    private static final String SETTINGS_FILENAME = ".tictactoe_settings.properties";
    private static final int DEFAULT_SERVER_PORT = 12345;


    private Socket clientSocket;
    private PrintWriter outToServer;
    private BufferedReader inFromServer;
    private Thread serverListenerThread;
    private volatile boolean connectedToServer = false;
    private String playerName = "Игрок";


    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        this.primaryStage = primaryStage;
        Random r = new Random();
        playerName = "Игрок" + (100 + r.nextInt(900));

        animationPane = new Pane();
        animationPane.setMouseTransparent(true);

        loadButtonImages();
        loadInitialSettings();

        this.mainMenuScene = createMainMenuScene();
        this.stylesScene = createStylesScene();
        this.gameScene = createGameScene();
        this.networkConfigScene = createNetworkConfigScene();

        updateColorsFromSchemeIndex();
        resetLocalGameLogic();

        primaryStage.setTitle("Крестики-нолики: Главное Меню");
        primaryStage.setScene(mainMenuScene);
        primaryStage.show();

        primaryStage.sceneProperty().addListener((obs, oldScene, newScene) -> {
            updateAnimationPane(oldScene, newScene);
            if (newScene == mainMenuScene || newScene == stylesScene || newScene == networkConfigScene) {
                startBackgroundAnimation();
            } else {
                stopBackgroundAnimation();
            }
        });

        updateAnimationPane(null, mainMenuScene);
        startBackgroundAnimation();

        primaryStage.setOnCloseRequest(event -> {
            saveCurrentSettings();
            disconnectFromServer();
            Platform.exit();
            System.exit(0);
        });
    }

    private void updateAnimationPane(Scene oldScene, Scene newScene) {
        if (oldScene != null && oldScene.getRoot() instanceof StackPane) {
            ((StackPane) oldScene.getRoot()).getChildren().remove(animationPane);
        }
        if (newScene != null && newScene.getRoot() instanceof StackPane) {
            StackPane root = (StackPane) newScene.getRoot();
            if (!root.getChildren().contains(animationPane)) {
                if (root.getChildren().isEmpty()) {
                    root.getChildren().add(animationPane);
                } else {
                    root.getChildren().add(0, animationPane);
                }
                animationPane.toBack();
            }
        }
    }

    private void loadButtonImages() {
        playButtonNormalImage = loadImage("/img/button_play_normal.png");
        playButtonHoverImage = loadImage("/img/button_play_hover.png");
        stylesButtonNormalImage = loadImage("/img/button_styles_normal.png");
        stylesButtonHoverImage = loadImage("/img/button_styles_hover.png");
        exitButtonNormalImage = loadImage("/img/button_exit_normal.png");
        exitButtonHoverImage = loadImage("/img/button_exit_hover.png");
        backButtonNormalImage = loadImage("/img/button_back_normal.png");
        backButtonHoverImage = loadImage("/img/button_back_hover.png");
        newGameIngameButtonNormalImage = loadImage("/img/button_newgame_ingame_normal.png");
        newGameIngameButtonHoverImage = loadImage("/img/button_newgame_ingame_hover.png");
        backToMenuIngameButtonNormalImage = loadImage("/img/button_menu_ingame_normal.png");
        backToMenuIngameButtonHoverImage = loadImage("/img/button_menu_ingame_hover.png");
        saveGameButtonNormalImage = loadImage("/img/button_save_normal.png");
        saveGameButtonHoverImage = loadImage("/img/button_save_hover.png");
        loadGameButtonNormalImage = loadImage("/img/button_load_normal.png");
        loadGameButtonHoverImage = loadImage("/img/button_load_hover.png");
        networkGameButtonNormalImage = loadImage("/img/button_network_normal.png");
        networkGameButtonHoverImage = loadImage("/img/button_network_hover.png");


        if (playButtonHoverImage == null && playButtonNormalImage != null) playButtonHoverImage = playButtonNormalImage;
        if (stylesButtonHoverImage == null && stylesButtonNormalImage != null) stylesButtonHoverImage = stylesButtonNormalImage;
        if (exitButtonHoverImage == null && exitButtonNormalImage != null) exitButtonHoverImage = exitButtonNormalImage;
        if (backButtonHoverImage == null && backButtonNormalImage != null) backButtonHoverImage = backButtonNormalImage;
        if (newGameIngameButtonNormalImage == null && playButtonNormalImage != null) newGameIngameButtonNormalImage = playButtonNormalImage;
        if (newGameIngameButtonHoverImage == null) newGameIngameButtonHoverImage = (newGameIngameButtonNormalImage == playButtonNormalImage && playButtonHoverImage != null) ? playButtonHoverImage : newGameIngameButtonNormalImage;
        if (backToMenuIngameButtonNormalImage == null && backButtonNormalImage != null) backToMenuIngameButtonNormalImage = backButtonNormalImage;
        if (backToMenuIngameButtonHoverImage == null) backToMenuIngameButtonHoverImage = (backToMenuIngameButtonNormalImage == backButtonNormalImage && backButtonHoverImage != null) ? backButtonHoverImage : backToMenuIngameButtonNormalImage;
        if (saveGameButtonNormalImage == null && stylesButtonNormalImage != null) saveGameButtonNormalImage = stylesButtonNormalImage;
        if (saveGameButtonHoverImage == null) saveGameButtonHoverImage = (saveGameButtonNormalImage == stylesButtonNormalImage && stylesButtonHoverImage != null) ? stylesButtonHoverImage : saveGameButtonNormalImage;
        if (loadGameButtonNormalImage == null && stylesButtonNormalImage != null) loadGameButtonNormalImage = stylesButtonNormalImage;
        if (loadGameButtonHoverImage == null) loadGameButtonHoverImage = (loadGameButtonNormalImage == stylesButtonNormalImage && stylesButtonHoverImage != null) ? stylesButtonHoverImage : loadGameButtonNormalImage;
        if (networkGameButtonNormalImage == null && playButtonNormalImage != null) networkGameButtonNormalImage = playButtonNormalImage;
        if (networkGameButtonHoverImage == null) networkGameButtonHoverImage = (networkGameButtonNormalImage == playButtonNormalImage && playButtonHoverImage != null) ? playButtonHoverImage : networkGameButtonNormalImage;
    }

    private Image loadImage(String path) {
        try {
            InputStream stream = getClass().getResourceAsStream(path);
            if (stream == null) {
                System.err.println("Не удалось найти ресурс изображения: " + path);
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
            Button fallbackButton = new Button("Img Err");
            fallbackButton.setPrefSize(width, height);
            fallbackButton.setOnAction(e -> action.run());
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
                pressDown.play();
                action.run();
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

    private StackPane createSceneRootPane(Node content) {
        StackPane stackPane = new StackPane(content);
        Stop[] stops = new Stop[]{
                new Stop(0, Color.web("#521d35")),
                new Stop(0.5, Color.web("#74460b")),
                new Stop(1, Color.web("#dbad73"))
        };
        LinearGradient lg = new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE, stops);
        stackPane.setBackground(new Background(new BackgroundFill(lg, CornerRadii.EMPTY, Insets.EMPTY)));
        return stackPane;
    }

    private Scene createMainMenuScene() {
        Label titleLabel = new Label("Крестики-нолики на Бесконечном Поле");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 28));
        titleLabel.setTextFill(Color.WHITE);

        double menuButtonWidth = 220;
        double menuButtonHeight = 65;

        Node playButton = createImageOnlyButton(playButtonNormalImage, playButtonHoverImage,
                menuButtonWidth, menuButtonHeight, () -> {
                    disconnectFromServer();
                    myPlayerSymbol = null;
                    primaryStage.setScene(gameScene);
                    primaryStage.setTitle("Крестики-нолики (Локально)");
                    resetLocalGameLogic();
                    updateGameStatusLabel();
                    redrawCanvas();
                });

        Node networkGameMenuButton = createImageOnlyButton(networkGameButtonNormalImage, networkGameButtonHoverImage,
                menuButtonWidth, menuButtonHeight, () -> {
                    primaryStage.setScene(networkConfigScene);
                    primaryStage.setTitle("Сетевая игра - Настройки");
                });

        Node loadGameMenuButton = createImageOnlyButton(loadGameButtonNormalImage, loadGameButtonHoverImage,
                menuButtonWidth, menuButtonHeight, () -> {
                    disconnectFromServer();
                    myPlayerSymbol = null;
                    loadGameAction();
                });

        Node saveGameMenuButton = createImageOnlyButton(saveGameButtonNormalImage, saveGameButtonHoverImage,
                menuButtonWidth, menuButtonHeight, () -> {
                    if (connectedToServer) {
                        showAlert("Сохранение", "Сохранение не доступно в сетевой игре.");
                        return;
                    }
                    saveGameAction();
                });

        Node stylesButton = createImageOnlyButton(stylesButtonNormalImage, stylesButtonHoverImage,
                menuButtonWidth, menuButtonHeight, () -> {
                    primaryStage.setScene(stylesScene);
                    primaryStage.setTitle("Настройка Стилей");
                });

        Node exitButton = createImageOnlyButton(exitButtonNormalImage, exitButtonHoverImage,
                menuButtonWidth, menuButtonHeight, Platform::exit);

        VBox menuButtonsLayout = new VBox(15, titleLabel, playButton, networkGameMenuButton, loadGameMenuButton, saveGameMenuButton, stylesButton, exitButton);
        menuButtonsLayout.setAlignment(Pos.CENTER);
        menuButtonsLayout.setPadding(new Insets(20));

        return new Scene(createSceneRootPane(menuButtonsLayout), 800, 700);
    }

    private Scene createNetworkConfigScene() {
        Label titleLabel = new Label("Настройки Сетевой Игры");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);

        TextField serverAddressField = new TextField("localhost");
        serverAddressField.setPromptText("Адрес сервера");
        serverAddressField.setMaxWidth(250);

        TextField serverPortField = new TextField(String.valueOf(DEFAULT_SERVER_PORT));
        serverPortField.setPromptText("Порт сервера");
        serverPortField.setMaxWidth(100);

        TextField playerNameField = new TextField(playerName);
        playerNameField.setPromptText("Ваше имя");
        playerNameField.setMaxWidth(200);

        HBox serverConfigLayout = new HBox(10, new Label("Сервер:"), serverAddressField, new Label("Порт:"), serverPortField);
        serverConfigLayout.setAlignment(Pos.CENTER);

        HBox nameLayout = new HBox(10, new Label("Имя:"), playerNameField);
        nameLayout.setAlignment(Pos.CENTER);

        Button connectButton = new Button("Подключиться к Серверу");
        connectButton.setFont(Font.font(16));
        connectButton.setOnAction(e -> {
            String address = serverAddressField.getText().trim();
            int port;
            try {
                port = Integer.parseInt(serverPortField.getText().trim());
            } catch (NumberFormatException ex) {
                showAlert("Ошибка", "Неверный формат порта.");
                return;
            }
            playerName = playerNameField.getText().trim();
            if (playerName.isEmpty()) {
                playerName = "Игрок" + (100 + new Random().nextInt(900));
            }
            connectToServer(address, port);
        });

        Node backButton = createImageOnlyButton(backButtonNormalImage, backButtonHoverImage, 220, 70,
                () -> primaryStage.setScene(mainMenuScene));

        VBox layout = new VBox(20, titleLabel, serverConfigLayout, nameLayout, connectButton, backButton);
        layout.setAlignment(Pos.CENTER);
        layout.setPadding(new Insets(30));

        return new Scene(createSceneRootPane(layout), 800, 600);
    }

    private void startBackgroundAnimation() {
        if (animationPane == null || animationPane.getScene() == null) return;
        if (backgroundAnimationTimeline != null) {
            backgroundAnimationTimeline.stop();
        }
        backgroundAnimationTimeline = new Timeline(new KeyFrame(Duration.seconds(0.7), event -> {
            if (animationPane.getScene() == null ||
                    !(primaryStage.getScene() == mainMenuScene ||
                      primaryStage.getScene() == stylesScene ||
                      primaryStage.getScene() == networkConfigScene)) {
                stopBackgroundAnimation();
                return;
            }
            if (animationPane.getChildren().size() < MAX_ANIMATED_SYMBOLS) {
                createAnimatedSymbol();
            }
        }));
        backgroundAnimationTimeline.setCycleCount(Timeline.INDEFINITE);
        backgroundAnimationTimeline.play();
    }

    private void stopBackgroundAnimation() {
        if (backgroundAnimationTimeline != null) {
            backgroundAnimationTimeline.stop();
            backgroundAnimationTimeline = null;
        }
    }

    private void createAnimatedSymbol() {
        if (animationPane == null || animationPane.getWidth() <= 0 || animationPane.getHeight() <= 0) return;
        String symbolText = random.nextBoolean() ? "X" : "O";
        Label symbolLabel = new Label(symbolText);
        double size = 30 + random.nextInt(120);
        symbolLabel.setFont(Font.font("Arial", FontWeight.BOLD, size));
        symbolLabel.setTextFill(animationColors.get(random.nextInt(animationColors.size())));
        symbolLabel.setOpacity(0);
        double x = random.nextDouble() * (animationPane.getWidth() - size);
        double y = random.nextDouble() * (animationPane.getHeight() - size);
        symbolLabel.setLayoutX(x);
        symbolLabel.setLayoutY(y);
        animationPane.getChildren().add(symbolLabel);

        FadeTransition fadeIn = new FadeTransition(Duration.seconds(FADE_IN_OUT_DURATION_SECONDS), symbolLabel);
        fadeIn.setToValue(0.5 + random.nextDouble() * 0.5);
        double lifeDuration = MIN_ANIM_DURATION_SECONDS + random.nextDouble() * (MAX_ANIM_DURATION_SECONDS - MIN_ANIM_DURATION_SECONDS);
        FadeTransition fadeOut = new FadeTransition(Duration.seconds(FADE_IN_OUT_DURATION_SECONDS), symbolLabel);
        fadeOut.setDelay(Duration.seconds(lifeDuration - FADE_IN_OUT_DURATION_SECONDS));
        fadeOut.setToValue(0);
        fadeIn.setOnFinished(e -> fadeOut.play());
        fadeOut.setOnFinished(e -> animationPane.getChildren().remove(symbolLabel));
        fadeIn.play();
    }

    private void updateColorsFromSchemeIndex() {
        if (currentColorSchemeIndex >= 0 && currentColorSchemeIndex < availableColorSchemes.size()) {
            ColorScheme scheme = availableColorSchemes.get(currentColorSchemeIndex);
            currentColorX = scheme.colorForX;
            currentColorO = scheme.colorForO;
        } else {
            currentColorSchemeIndex = 0;
            ColorScheme scheme = availableColorSchemes.get(0);
            currentColorX = scheme.colorForX;
            currentColorO = scheme.colorForO;
        }
    }

    private Scene createStylesScene() {
        Label titleLabel = new Label("Выберите Цветовую Схему");
        titleLabel.setFont(Font.font("Arial", FontWeight.BOLD, 24));
        titleLabel.setTextFill(Color.WHITE);

        VBox schemesOptionsLayout = new VBox(15);
        schemesOptionsLayout.setAlignment(Pos.CENTER_LEFT);
        schemesOptionsLayout.setPadding(new Insets(20));
        schemesOptionsLayout.setMaxWidth(600);

        ToggleGroup group = new ToggleGroup();
        for (int i = 0; i < availableColorSchemes.size(); i++) {
            ColorScheme scheme = availableColorSchemes.get(i);
            RadioButton rb = new RadioButton(scheme.name);
            rb.setUserData(i);
            rb.setToggleGroup(group);
            rb.setFont(Font.font(16));
            rb.setTextFill(Color.WHITE);

            HBox colorSampleBox = new HBox(10);
            colorSampleBox.setAlignment(Pos.CENTER_LEFT);
            Label xText = new Label("X:");
            xText.setFont(Font.font(14));
            xText.setTextFill(Color.WHITE);
            Rectangle xRect = new Rectangle(20, 20, scheme.colorForX);
            xRect.setStroke(Color.DARKGRAY);
            Label oText = new Label("O:");
            oText.setFont(Font.font(14));
            oText.setTextFill(Color.WHITE);
            Rectangle oRect = new Rectangle(20, 20, scheme.colorForO);
            oRect.setStroke(Color.DARKGRAY);
            colorSampleBox.getChildren().addAll(xText, xRect, new Label("  "), oText, oRect);

            HBox line = new HBox(20, rb, colorSampleBox);
            line.setAlignment(Pos.CENTER_LEFT);
            schemesOptionsLayout.getChildren().add(line);

            if (i == currentColorSchemeIndex) {
                rb.setSelected(true);
            }
        }

        group.selectedToggleProperty().addListener((obs, oldToggle, newToggle) -> {
            if (newToggle != null) {
                currentColorSchemeIndex = (int) newToggle.getUserData();
                updateColorsFromSchemeIndex();
                if (primaryStage.getScene() == gameScene) {
                    updateGameStatusLabel();
                    redrawCanvas();
                }
                saveCurrentSettings();
            }
        });

        Node backButtonNode = createImageOnlyButton(backButtonNormalImage, backButtonHoverImage, 220, 70,
                () -> primaryStage.setScene(mainMenuScene));

        VBox contentLayout = new VBox(30, titleLabel, schemesOptionsLayout, backButtonNode);
        contentLayout.setAlignment(Pos.CENTER);
        contentLayout.setPadding(new Insets(30));

        return new Scene(createSceneRootPane(contentLayout), 800, 600);
    }

    private Scene createGameScene() {
        BorderPane gameRootLayout = new BorderPane();
        statusTextNode = new Text();
        statusTextNode.setFont(Font.font("Arial", FontWeight.BOLD, 22));
        statusTextNode.setStroke(Color.WHITE);
        statusTextNode.setStrokeWidth(1.0);

        double ingameButtonWidth = 160;
        double ingameButtonHeight = 50;

        Node newGameInGameButton = createImageOnlyButton(newGameIngameButtonNormalImage, newGameIngameButtonHoverImage,
                ingameButtonWidth, ingameButtonHeight, () -> {
                    if (connectedToServer) {
                        sendMessageToServer("NEW_GAME_REQUEST");
                    } else {
                        resetLocalGameLogic();
                        redrawCanvas();
                    }
                });

        Node backToMenuGameButton = createImageOnlyButton(backToMenuIngameButtonNormalImage, backToMenuIngameButtonHoverImage,
                ingameButtonWidth, ingameButtonHeight, () -> {
                    disconnectFromServer();
                    primaryStage.setScene(mainMenuScene);
                    primaryStage.setTitle("Крестики-нолики: Главное Меню");
                });
        
        opponentNameLabel = new Label("Оппонент: ...");
        opponentNameLabel.setFont(Font.font("Arial", FontWeight.BOLD, 16));
        opponentNameLabel.setTextFill(Color.LIGHTGRAY);

        HBox gameTopPanel = new HBox(15, statusTextNode, opponentNameLabel, newGameInGameButton, backToMenuGameButton);
        gameTopPanel.setAlignment(Pos.CENTER_LEFT);
        gameTopPanel.setPadding(new Insets(10));
        HBox.setHgrow(statusTextNode, Priority.ALWAYS);
        HBox.setHgrow(opponentNameLabel, Priority.ALWAYS);

        Stop[] stops = new Stop[]{ new Stop(0, Color.web("#521d35")), new Stop(0.5, Color.web("#74460b")), new Stop(1, Color.web("#dbad73"))};
        LinearGradient lg = new LinearGradient(0,0,0,1,true,CycleMethod.NO_CYCLE,stops);
        gameTopPanel.setBackground(new Background(new BackgroundFill(lg, CornerRadii.EMPTY, Insets.EMPTY)));
        gameRootLayout.setTop(gameTopPanel);

        canvas = new Canvas();
        gc = canvas.getGraphicsContext2D();
        BorderPane canvasContainer = new BorderPane(canvas);
        gameRootLayout.setCenter(canvasContainer);

        chatArea = new TextArea();
        chatArea.setEditable(false);
        chatArea.setPrefHeight(100);
        chatArea.setWrapText(true);

        chatInput = new TextField();
        chatInput.setPromptText("Введите сообщение...");
        chatInput.setOnAction(e -> sendChatMessage());

        Button sendChatButton = new Button("Отправить");
        sendChatButton.setOnAction(e -> sendChatMessage());

        HBox chatInputBox = new HBox(5, chatInput, sendChatButton);
        HBox.setHgrow(chatInput, Priority.ALWAYS);

        VBox chatLayout = new VBox(5, new Label("Чат:"), chatArea, chatInputBox);
        chatLayout.setPadding(new Insets(10));
        chatLayout.setMaxWidth(250);
        gameRootLayout.setRight(chatLayout);

        canvas.widthProperty().bind(canvasContainer.widthProperty());
        canvas.heightProperty().bind(canvasContainer.heightProperty());
        canvas.widthProperty().addListener(o -> redrawCanvas());
        canvas.heightProperty().addListener(o -> redrawCanvas());
        canvas.setOnMousePressed(this::handleMousePressed);
        canvas.setOnMouseDragged(this::handleMouseDragged);
        canvas.setOnMouseReleased(this::handleMouseReleased);
        canvas.setOnScroll(this::handleMouseScroll);

        return new Scene(gameRootLayout, 1050, 700);
    }

    private void resetLocalGameLogic() {
        boardData.clear();
        currentPlayerForLocalGame = Player.X;
        gameOver = false;
        myTurn = !connectedToServer;
        currentTurnPlayerNetwork = null;
        opponentPlayerName = "?";

        if (statusTextNode != null) {
            updateGameStatusLabel();
        }
        if (opponentNameLabel != null) {
            Platform.runLater(() -> opponentNameLabel.setText("Локальная игра"));
        }
    }

    private void resetNetworkGame() {
        boardData.clear();
        gameOver = false;
        Platform.runLater(() -> {
            updateGameStatusLabel();
            redrawCanvas();
            if (opponentNameLabel != null) {
                opponentNameLabel.setText(opponentPlayerName.equals("?") ? "Оппонент: Ожидание..." : "Оппонент: " + opponentPlayerName);
            }
        });
    }

    private void updateGameStatusLabel() {
        if (statusTextNode == null) return;
        Platform.runLater(() -> {
            String text;
            Color fillColor = Color.WHITE;
            if (gameOver) {
                Player winner = connectedToServer ? currentTurnPlayerNetwork : currentPlayerForLocalGame;
                if (winner == null && connectedToServer && currentTurnPlayerNetwork == null) {
                    text = "Ничья!";
                } else if (winner != null) {
                    text = "Игрок " + winner + " выиграл!";
                    fillColor = (winner == Player.X) ? currentColorX : currentColorO;
                } else {
                    text = "Игра окончена!";
                }
            } else if (!connectedToServer) {
                text = "Ход игрока: " + currentPlayerForLocalGame;
                fillColor = (currentPlayerForLocalGame == Player.X) ? currentColorX : currentColorO;
            } else {
                if (myPlayerSymbol == null) {
                    text = "Подключение...";
                } else if (myTurn) {
                    text = "Ваш ход (" + myPlayerSymbol + ")";
                    fillColor = (myPlayerSymbol == Player.X) ? currentColorX : currentColorO;
                } else {
                    text = "Ход оппонента (" + (currentTurnPlayerNetwork != null ? currentTurnPlayerNetwork : "?") + ")";
                    if (currentTurnPlayerNetwork != null) {
                        fillColor = (currentTurnPlayerNetwork == Player.X) ? currentColorX : currentColorO;
                    }
                }
            }
            statusTextNode.setText(text);
            statusTextNode.setFill(fillColor);
        });
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
            double deltaX = event.getX() - lastMousePressCanvasPosition.getX();
            double deltaY = event.getY() - lastMousePressCanvasPosition.getY();
            if (!isDragging) {
                if (Math.sqrt(deltaX * deltaX + deltaY * deltaY) > DRAG_THRESHOLD) {
                    isDragging = true;
                    if (canvas != null) canvas.setCursor(Cursor.MOVE);
                }
            }
            if (isDragging) {
                viewLogicalX = viewOriginAtMousePress.getX() - deltaX / CELL_SIZE;
                viewLogicalY = viewOriginAtMousePress.getY() - deltaY / CELL_SIZE;
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
            if (canvas != null) canvas.setCursor(Cursor.DEFAULT);
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
        if (connectedToServer) {
            if (!myTurn || myPlayerSymbol == null) {
                Platform.runLater(() -> { if(chatArea != null) chatArea.appendText("СИСТЕМА: Не ваш ход!\n"); });
                return;
            }
        } else {
            if (currentPlayerForLocalGame == null) return;
        }
        double canvasX = event.getX();
        double canvasY = event.getY();
        int logicalCol = (int) Math.floor(viewLogicalX + canvasX / CELL_SIZE);
        int logicalRow = (int) Math.floor(viewLogicalY + canvasY / CELL_SIZE);
        String cellKey = logicalRow + "," + logicalCol;

        if (!boardData.containsKey(cellKey)) {
            if (connectedToServer) {
                sendMessageToServer("MOVE " + logicalRow + "," + logicalCol);
            } else {
                boardData.put(cellKey, currentPlayerForLocalGame);
                if (checkWin(logicalRow, logicalCol, currentPlayerForLocalGame)) {
                    gameOver = true;
                    updateGameStatusLabel();
                    showAlert("Игра окончена", "Игрок " + currentPlayerForLocalGame + " выиграл!");
                } else {
                    currentPlayerForLocalGame = currentPlayerForLocalGame.opposite();
                    updateGameStatusLabel();
                }
                redrawCanvas();
            }
        }
    }

    private void redrawCanvas() {
        if (gc == null || canvas == null || canvas.getWidth() <= 0 || canvas.getHeight() <= 0) return;
        Platform.runLater(() -> {
            gc.clearRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setFill(Color.WHITE);
            gc.fillRect(0, 0, canvas.getWidth(), canvas.getHeight());
            gc.setStroke(Color.BLACK);
            gc.setLineWidth(Math.max(0.3, Math.min(1.5, CELL_SIZE / 80.0)));

            double startLogicalCol = viewLogicalX;
            double endLogicalCol = viewLogicalX + canvas.getWidth() / CELL_SIZE;
            double startLogicalRow = viewLogicalY;
            double endLogicalRow = viewLogicalY + canvas.getHeight() / CELL_SIZE;

            double firstVerticalLineX = (Math.ceil(startLogicalCol) - startLogicalCol) * CELL_SIZE;
            for (double x = firstVerticalLineX; x < canvas.getWidth(); x += CELL_SIZE) {
                gc.strokeLine(Math.round(x), 0, Math.round(x), canvas.getHeight());
            }
            double firstHorizontalLineY = (Math.ceil(startLogicalRow) - startLogicalRow) * CELL_SIZE;
            for (double y = firstHorizontalLineY; y < canvas.getHeight(); y += CELL_SIZE) {
                gc.strokeLine(0, Math.round(y), canvas.getWidth(), Math.round(y));
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
                        double cellCenterX = (c - viewLogicalX + 0.5) * CELL_SIZE;
                        double cellCenterY = (r - viewLogicalY + 0.5) * CELL_SIZE;
                        gc.fillText(player.toString(), Math.round(cellCenterX), Math.round(cellCenterY + CELL_SIZE * 0.25));
                    }
                }
            }
        });
    }

    private Player getPlayerAt(int logicalRow, int logicalCol) {
        return boardData.get(logicalRow + "," + logicalCol);
    }

    private boolean checkWin(int lastMoveRow, int lastMoveCol, Player player) {
        if (player == null) return false;
        if (countConsecutive(lastMoveRow, lastMoveCol, player, 0, 1) + countConsecutive(lastMoveRow, lastMoveCol, player, 0, -1) - 1 >= WIN_LENGTH) return true;
        if (countConsecutive(lastMoveRow, lastMoveCol, player, 1, 0) + countConsecutive(lastMoveRow, lastMoveCol, player, -1, 0) - 1 >= WIN_LENGTH) return true;
        if (countConsecutive(lastMoveRow, lastMoveCol, player, 1, 1) + countConsecutive(lastMoveRow, lastMoveCol, player, -1, -1) - 1 >= WIN_LENGTH) return true;
        if (countConsecutive(lastMoveRow, lastMoveCol, player, 1, -1) + countConsecutive(lastMoveRow, lastMoveCol, player, -1, 1) - 1 >= WIN_LENGTH) return true;
        return false;
    }

    private int countConsecutive(int r, int c, Player player, int dr, int dc) {
        int count = 0;
        for (int i = 1; i < WIN_LENGTH; i++) {
            if (player.equals(getPlayerAt(r + i * dr, c + i * dc))) {
                count++;
            } else {
                break;
            }
        }
        return count + 1;
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void saveGameAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Сохранить локальную игру");
        fileChooser.setInitialFileName(DEFAULT_SAVE_FILENAME);
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Сохранения (*.properties)", "*.properties"));
        File file = fileChooser.showSaveDialog(primaryStage);
        if (file != null) {
            saveGameToFile(file);
        }
    }

    private void loadGameAction() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Загрузить локальную игру");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Сохранения (*.properties)", "*.properties"));
        File file = fileChooser.showOpenDialog(primaryStage);
        if (file != null) {
            loadGameFromFile(file);
            if (!boardData.isEmpty()) {
                primaryStage.setScene(gameScene);
                primaryStage.setTitle("Крестики-нолики (Загружено)");
                updateGameStatusLabel();
                redrawCanvas();
            }
        }
    }

    private void saveGameToFile(File file) {
        Properties props = new Properties();
        props.setProperty("cellSize", Double.toString(CELL_SIZE));
        props.setProperty("viewLogicalX", Double.toString(viewLogicalX));
        props.setProperty("viewLogicalY", Double.toString(viewLogicalY));
        props.setProperty("currentPlayerForLocalGame", currentPlayerForLocalGame.name());
        props.setProperty("gameOver", Boolean.toString(gameOver));
        String boardDataString = boardData.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().name())
                .collect(Collectors.joining(";"));
        props.setProperty("boardData", boardDataString);
        try (OutputStream out = new FileOutputStream(file)) {
            props.store(out, "Tic Tac Toe Local Game Save");
            showAlert("Сохранение", "Локальная игра сохранена в " + file.getName());
        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Ошибка сохранения", "Не удалось сохранить: " + e.getMessage());
        }
    }

    private void loadGameFromFile(File file) {
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(file)) {
            props.load(in);
            CELL_SIZE = Double.parseDouble(props.getProperty("cellSize", "40.0"));
            viewLogicalX = Double.parseDouble(props.getProperty("viewLogicalX", "-10.0"));
            viewLogicalY = Double.parseDouble(props.getProperty("viewLogicalY", "-7.0"));
            currentPlayerForLocalGame = Player.valueOf(props.getProperty("currentPlayerForLocalGame", "X"));
            gameOver = Boolean.parseBoolean(props.getProperty("gameOver", "false"));
            boardData.clear();
            String boardDataString = props.getProperty("boardData", "");
            if (!boardDataString.isEmpty()) {
                String[] entries = boardDataString.split(";");
                for (String entry : entries) {
                    String[] parts = entry.split("=");
                    if (parts.length == 2) {
                        boardData.put(parts[0], Player.valueOf(parts[1]));
                    }
                }
            }
            myPlayerSymbol = null;
            myTurn = (currentPlayerForLocalGame == Player.X && !gameOver);
        } catch (IOException | IllegalArgumentException e) {
            e.printStackTrace();
            showAlert("Ошибка загрузки", "Не удалось загрузить: " + e.getMessage());
        }
    }

    private File getSettingsFile() {
        return new File(System.getProperty("user.home"), SETTINGS_FILENAME);
    }

    private void saveCurrentSettings() {
        Properties props = new Properties();
        props.setProperty("cellSize", Double.toString(CELL_SIZE));
        props.setProperty("viewLogicalX", Double.toString(viewLogicalX));
        props.setProperty("viewLogicalY", Double.toString(viewLogicalY));
        props.setProperty("colorSchemeIndex", Integer.toString(currentColorSchemeIndex));
        props.setProperty("playerName", playerName);
        try (OutputStream out = new FileOutputStream(getSettingsFile())) {
            props.store(out, "Tic Tac Toe Settings");
            System.out.println("Настройки сохранены.");
        } catch (IOException e) {
            System.err.println("Не удалось сохранить настройки: " + e.getMessage());
        }
    }

    private void loadInitialSettings() {
        File settingsFile = getSettingsFile();
        if (!settingsFile.exists()) {
            System.out.println("Файл настроек не найден.");
            return;
        }
        Properties props = new Properties();
        try (InputStream in = new FileInputStream(settingsFile)) {
            props.load(in);
            CELL_SIZE = Double.parseDouble(props.getProperty("cellSize", Double.toString(CELL_SIZE)));
            viewLogicalX = Double.parseDouble(props.getProperty("viewLogicalX", Double.toString(viewLogicalX)));
            viewLogicalY = Double.parseDouble(props.getProperty("viewLogicalY", Double.toString(viewLogicalY)));
            currentColorSchemeIndex = Integer.parseInt(props.getProperty("colorSchemeIndex", Integer.toString(currentColorSchemeIndex)));
            playerName = props.getProperty("playerName", playerName);
            System.out.println("Настройки загружены.");
        } catch (IOException | NumberFormatException e) {
            System.err.println("Ошибка загрузки настроек: " + e.getMessage());
        }
    }

    private void connectToServer(String address, int port) {
        if (connectedToServer) {
            showAlert("Подключение", "Вы уже подключены.");
            return;
        }
        try {
            clientSocket = new Socket(address, port);
            outToServer = new PrintWriter(clientSocket.getOutputStream(), true);
            inFromServer = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            connectedToServer = true;
            sendMessageToServer("CONNECT " + playerName);

            serverListenerThread = new Thread(this::listenToServer);
            serverListenerThread.setDaemon(true);
            serverListenerThread.start();

            primaryStage.setScene(gameScene);
            primaryStage.setTitle("Крестики-нолики (Онлайн - " + playerName + ")");
            resetNetworkGame();
            Platform.runLater(() -> {
                if (chatArea != null) {
                    chatArea.clear();
                    chatArea.appendText("СИСТЕМА: Подключено к " + address + ":" + port + "\n");
                }
            });
        } catch (IOException e) {
            showAlert("Ошибка подключения", "Не удалось подключиться: " + e.getMessage());
            connectedToServer = false;
        }
    }

    private void disconnectFromServer() {
        if (connectedToServer) {
            try {
                if (outToServer != null) outToServer.println("DISCONNECT");
                if (serverListenerThread != null) serverListenerThread.interrupt();
                if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            } catch (IOException e) { /* ignore close errors */ }
            finally {
                clientSocket = null;
                outToServer = null;
                inFromServer = null;
                connectedToServer = false;
                serverListenerThread = null;
                myPlayerSymbol = null;
                myTurn = false;
                currentTurnPlayerNetwork = null;
                opponentPlayerName = "?";
                Platform.runLater(() -> {
                    if (chatArea != null) chatArea.appendText("СИСТЕМА: Отключено от сервера.\n");
                    if (opponentNameLabel != null) opponentNameLabel.setText("Оппонент: ?");
                    updateGameStatusLabel();
                });
                System.out.println("Отключено от сервера.");
            }
        }
    }

    private void sendMessageToServer(String message) {
        if (connectedToServer && outToServer != null) {
            outToServer.println(message);
        } else if (!message.startsWith("CONNECT")) {
            System.err.println("Попытка отправить сообщение '" + message + "' без подключения.");
        }
    }

    private void listenToServer() {
        try {
            String serverMessage;
            while (connectedToServer && clientSocket != null && !clientSocket.isClosed() &&
                   (serverMessage = inFromServer.readLine()) != null) {
                System.out.println("От сервера: " + serverMessage);
                final String finalMessage = serverMessage;
                Platform.runLater(() -> processServerMessage(finalMessage));
            }
        } catch (SocketException e) {
            if (connectedToServer) {
                Platform.runLater(() -> {
                    showAlert("Ошибка сети", "Соединение с сервером потеряно: " + e.getMessage());
                    disconnectFromServer();
                });
            }
        } catch (IOException e) {
            if (connectedToServer) {
                Platform.runLater(() -> {
                    showAlert("Ошибка сети", "Ошибка чтения с сервера: " + e.getMessage());
                    disconnectFromServer();
                });
            }
        } finally {
            if (connectedToServer) {
                disconnectFromServer();
            }
        }
    }

    private void processServerMessage(String message) {
        String[] parts = message.split(" ", 2);
        String command = parts[0];
        String payload = parts.length > 1 ? parts[1] : "";

        switch (command) {
            case "WELCOME":
                myPlayerSymbol = Player.valueOf(payload);
                if (chatArea != null) chatArea.appendText("СИСТЕМА: Вы играете за " + myPlayerSymbol + "\n");
                resetNetworkGame();
                break;
            case "OPPONENT_CONNECTED":
                opponentPlayerName = payload;
                if (chatArea != null) chatArea.appendText("СИСТЕМА: Игрок '" + opponentPlayerName + "' подключился.\n");
                if (opponentNameLabel != null) Platform.runLater(() -> opponentNameLabel.setText("Оппонент: " + opponentPlayerName));
                break;
            case "OPPONENT_DISCONNECTED":
                if (chatArea != null) chatArea.appendText("СИСТЕМА: Оппонент '" + opponentPlayerName + "' отключился. Игра окончена.\n");
                gameOver = true;
                myTurn = false;
                currentTurnPlayerNetwork = null;
                opponentPlayerName = "?";
                updateGameStatusLabel();
                if (opponentNameLabel != null) Platform.runLater(() -> opponentNameLabel.setText("Оппонент: Отключен"));
                break;
            case "BOARD_UPDATE":
                updateBoardFromString(payload);
                redrawCanvas();
                break;
            case "TURN":
                currentTurnPlayerNetwork = Player.valueOf(payload);
                myTurn = (myPlayerSymbol != null && myPlayerSymbol == currentTurnPlayerNetwork && !gameOver);
                updateGameStatusLabel();
                break;
            case "GAME_OVER":
                gameOver = true;
                myTurn = false;
                if (payload.equals("DRAW")) {
                    currentTurnPlayerNetwork = null;
                    if (chatArea != null) chatArea.appendText("СИСТЕМА: Игра окончена. Ничья!\n");
                } else {
                    currentTurnPlayerNetwork = Player.valueOf(payload);
                    if (chatArea != null) chatArea.appendText("СИСТЕМА: Игра окончена. Победитель: " + currentTurnPlayerNetwork + "\n");
                }
                updateGameStatusLabel();
                break;
            case "NEW_GAME_CONFIRMED":
                if (chatArea != null) chatArea.appendText("СИСТЕМА: Новая игра начинается!\n");
                resetNetworkGame();

                if (opponentNameLabel != null) Platform.runLater(() -> opponentNameLabel.setText(opponentPlayerName.equals("?") ? "Оппонент: Ожидание..." : "Оппонент: " + opponentPlayerName));
                break;
            case "CHAT_MSG":
                if (chatArea != null) chatArea.appendText(payload + "\n");
                break;
            case "ERROR":
                if (chatArea != null) chatArea.appendText("СЕРВЕР ОШИБКА: " + payload + "\n");
                break;
            case "WAITING_FOR_OPPONENT":
                if (chatArea != null) chatArea.appendText("СИСТЕМА: Ожидание второго игрока...\n");
                opponentPlayerName = "?";
                if (opponentNameLabel != null) Platform.runLater(() -> opponentNameLabel.setText("Оппонент: Ожидание..."));
                break;
            default:
                System.err.println("Неизвестная команда от сервера: " + message);
        }
    }

    private void updateBoardFromString(String boardStr) {
        boardData.clear();
        if (boardStr.isEmpty()) return;
        String[] entries = boardStr.split(";");
        for (String entry : entries) {
            String[] parts = entry.split("=");
            if (parts.length == 2) {
                boardData.put(parts[0], Player.valueOf(parts[1]));
            }
        }
    }

    private void sendChatMessage() {
        String message = chatInput.getText().trim();
        if (!message.isEmpty() && connectedToServer) {
            sendMessageToServer("CHAT " + message);
            chatInput.clear();
        } else if (!connectedToServer && !message.isEmpty() && chatArea != null) {
            chatArea.appendText("СИСТЕМА: Вы не подключены.\n");
        }
    }
}