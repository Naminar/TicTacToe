import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class TicTacToeServer {

    private enum Player {
        X, O;
        public Player opposite() {
            return this == X ? O : X;
        }
    }

    public static final int DEFAULT_PORT = 12345;
    private static final int WIN_LENGTH = 5;

    private final int port;
    private ServerSocket serverSocket;
    private final Map<Player, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Map<String, Player> boardDataServer = new ConcurrentHashMap<>();
    private Player currentPlayerServer = Player.X;
    private boolean gameRunning = false;
    private volatile boolean running = false;
    private Player lastWinner = null;

    public TicTacToeServer(int port) {
        this.port = port;
    }

    public boolean isRunning() {
        return running && serverSocket != null && !serverSocket.isClosed();
    }

    public void start() {
        running = true;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Сервер \"Крестики-нолики\" запущен на порту " + port);
            printServerAddresses();

            while (running) {
                if (clients.size() < 2) {
                    System.out.println("Ожидание подключения клиента...");
                    Socket clientSocket = serverSocket.accept();
                    if (!running) {
                        clientSocket.close();
                        break;
                    }
                    System.out.println("Клиент подключился: " + clientSocket.getInetAddress());
                    assignPlayerAndStartHandler(clientSocket);
                } else {
                    Thread.sleep(100);
                }
            }
        } catch (SocketException e) {
            if (running) {
                System.err.println("SocketException на сервере: " + e.getMessage());
            } else {
                System.out.println("Сервер остановлен (SocketException при accept).");
            }
        } catch (IOException e) {
            if (running) {
                System.err.println("Ошибка ввода-вывода на сервере: " + e.getMessage());
            }
        } catch (InterruptedException e) {
            System.out.println("Поток сервера прерван.");
            Thread.currentThread().interrupt();
        } finally {
            stopServerInternal();
        }
    }

    private void printServerAddresses() {
        System.out.println("Клиенты могут подключаться по следующим адресам:");
        try {

            System.out.println("- localhost:" + port);
            System.out.println("- 127.0.0.1:" + port);

            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            for (NetworkInterface netint : Collections.list(nets)) {
                if (netint.isLoopback() || !netint.isUp() || netint.isVirtual()) {
                    continue;
                }
                Enumeration<InetAddress> inetAddresses = netint.getInetAddresses();
                for (InetAddress inetAddress : Collections.list(inetAddresses)) {


                    if (inetAddress.isSiteLocalAddress() && !inetAddress.isLoopbackAddress()) {

                        if (inetAddress.getHostAddress().matches("\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}")) {
                            System.out.println("- " + inetAddress.getHostAddress() + ":" + port + " (Интерфейс: " + netint.getDisplayName() + ")");
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Не удалось получить IP-адреса сетевых интерфейсов: " + e.getMessage());
        }
        System.out.println("--------------------------------------------------");
    }


    private void assignPlayerAndStartHandler(Socket clientSocket) {
        Player symbol = clients.containsKey(Player.X) ? Player.O : Player.X;
        ClientHandler handler = new ClientHandler(clientSocket, symbol, this);
        clients.put(symbol, handler);
        new Thread(handler).start();

        if (clients.size() == 1) {
            handler.sendMessage("WAITING_FOR_OPPONENT");
        }


    }

    public void stopServer() {
        running = false;
        stopServerInternal();
    }

    private void stopServerInternal() {
        System.out.println("Остановка сервера...");
        running = false;
        clients.values().forEach(ClientHandler::closeConnection);
        clients.clear();
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
                System.out.println("ServerSocket закрыт.");
            } catch (IOException e) {
                System.err.println("Ошибка при закрытии ServerSocket: " + e.getMessage());
            }
        }
        System.out.println("Сервер полностью остановлен.");
    }

    public synchronized void startGameProcedure() {
        boardDataServer.clear();

        if (lastWinner != null) {
            currentPlayerServer = lastWinner;
            System.out.println("Предыдущий победитель (" + lastWinner + ") ходит первым.");
        } else {
            currentPlayerServer = Player.X;
            System.out.println("Новая игра или предыдущая ничья, X ходит первым.");
        }

        gameRunning = true;

        broadcastMessage("NEW_GAME_CONFIRMED");
        broadcastBoard();
        broadcastTurn();
    }

    public synchronized void handlePlayerMove(Player player, int row, int col, ClientHandler mover) {
        if (!gameRunning) {
            mover.sendMessage("ERROR Игра еще не началась или уже окончена.");
            return;
        }
        if (player != currentPlayerServer) {
            mover.sendMessage("ERROR Не ваш ход.");
            return;
        }
        String key = row + "," + col;
        if (boardDataServer.containsKey(key)) {
            mover.sendMessage("ERROR Ячейка занята.");
            return;
        }

        boardDataServer.put(key, player);
        System.out.println("Игрок " + player + " (" + mover.getPlayerName() + ") сделал ход: " + key);
        broadcastBoard();

        if (checkWinServer(row, col, player)) {
            System.out.println("Игрок " + player + " (" + mover.getPlayerName() + ") выиграл!");
            lastWinner = player;
            broadcastMessage("GAME_OVER " + player.name());
            gameRunning = false;
        } else if (isBoardFull()) {
            System.out.println("Ничья! Доска заполнена (условное ограничение).");
            lastWinner = null;
            broadcastMessage("GAME_OVER DRAW");
            gameRunning = false;
        } else {
            currentPlayerServer = currentPlayerServer.opposite();
            broadcastTurn();
        }
    }

    private boolean isBoardFull() {
        return boardDataServer.size() >= 200;
    }

    public synchronized void requestNewGame(ClientHandler requester) {
        if (clients.size() < 2) {
            requester.sendMessage("ERROR Недостаточно игроков для новой игры.");
            return;
        }
        System.out.println("Игрок " + requester.getSymbol() + " (" + requester.getPlayerName() + ") запросил новую игру.");
        startGameProcedure();
    }

    public boolean checkWinServer(int r, int c, Player p) {
        if (countConsecutiveServer(r, c, p, 0, 1) + countConsecutiveServer(r, c, p, 0, -1) - 1 >= WIN_LENGTH) return true;
        if (countConsecutiveServer(r, c, p, 1, 0) + countConsecutiveServer(r, c, p, -1, 0) - 1 >= WIN_LENGTH) return true;
        if (countConsecutiveServer(r, c, p, 1, 1) + countConsecutiveServer(r, c, p, -1, -1) - 1 >= WIN_LENGTH) return true;
        if (countConsecutiveServer(r, c, p, 1, -1) + countConsecutiveServer(r, c, p, -1, 1) - 1 >= WIN_LENGTH) return true;
        return false;
    }

    private int countConsecutiveServer(int r, int c, Player p, int dr, int dc) {
        int count = 0;
        for (int i = 1; i < WIN_LENGTH; i++) {
            if (p.equals(boardDataServer.get((r + i * dr) + "," + (c + i * dc)))) {
                count++;
            } else {
                break;
            }
        }
        return count + 1;
    }

    private void broadcastBoard() {
        String boardStr = boardDataServer.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue().name())
                .collect(Collectors.joining(";"));
        broadcastMessage("BOARD_UPDATE " + boardStr);
    }

    private void broadcastTurn() {
        broadcastMessage("TURN " + currentPlayerServer.name());
    }

    public void broadcastMessage(String message) {
        for (ClientHandler handler : clients.values()) {
            if (handler != null) {
                handler.sendMessage(message);
            }
        }
    }

    public void broadcastChatMessage(Player senderSymbol, String senderName, String chatMessage) {
        String fullMessage = "CHAT_MSG " + senderName + " (" + senderSymbol + "): " + chatMessage;
        for (ClientHandler handler : clients.values()) {
            if (handler != null) {
                handler.sendMessage(fullMessage);
            }
        }
    }

    public void removeClient(ClientHandler handler) {
        if (handler == null) return;
        Player symbol = handler.getSymbol();
        clients.remove(symbol);
        System.out.println("Клиент " + symbol + " (" + handler.getPlayerName() + ") отключился.");
        if (gameRunning) {
            gameRunning = false;
            clients.values().forEach(remainingHandler -> {
                if (remainingHandler != null) {
                    remainingHandler.sendMessage("OPPONENT_DISCONNECTED");
                    remainingHandler.sendMessage("ERROR Игра прервана из-за отключения оппонента.");
                }
            });
        }
        if (clients.size() == 1) {
            ClientHandler remainingHandler = clients.values().stream().findFirst().orElse(null);
            if (remainingHandler != null) {
                remainingHandler.sendMessage("WAITING_FOR_OPPONENT");
            }
        }
    }

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                System.err.println("Неверный порт: " + args[0] + ". Используется порт по умолчанию: " + DEFAULT_PORT);
            }
        }
        TicTacToeServer server = new TicTacToeServer(port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stopServer));
        server.start();
    }

    private static class ClientHandler implements Runnable {
        private final Socket socket;
        private final Player symbol;
        private final TicTacToeServer server;
        private PrintWriter out;
        private BufferedReader in;
        private String playerName = "Аноним";

        public ClientHandler(Socket socket, Player symbol, TicTacToeServer server) {
            this.socket = socket;
            this.symbol = symbol;
            this.server = server;
            try {
                this.out = new PrintWriter(socket.getOutputStream(), true);
                this.in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            } catch (IOException e) {
                System.err.println("Ошибка создания потоков для клиента " + symbol + ": " + e.getMessage());
            }
        }

        public Player getSymbol() { return symbol; }
        public String getPlayerName() { return playerName; }

        @Override
        public void run() {
            try {
                sendMessage("WELCOME " + symbol.name());
                String clientMessage;
                while (server.running && socket != null && !socket.isClosed() &&
                       (clientMessage = in.readLine()) != null) {
                    
                    String[] parts = clientMessage.split(" ", 2);
                    String command = parts[0].toUpperCase();
                    String payload = parts.length > 1 ? parts[1] : "";

                    switch (command) {
                        case "CONNECT":
                            this.playerName = payload.isEmpty() ? "Игрок_" + symbol : payload;
                            System.out.println("Игрок " + symbol + " теперь известен как '" + this.playerName + "'");
                            
                            synchronized (server.clients) {
                                if (server.clients.size() == 2) {
                                    ClientHandler otherHandler = server.clients.get(symbol.opposite());
                                    if (otherHandler != null && !otherHandler.getPlayerName().equals("Аноним")) {
                                        otherHandler.sendMessage("OPPONENT_CONNECTED " + this.playerName);
                                        this.sendMessage("OPPONENT_CONNECTED " + otherHandler.getPlayerName());
                                        if (!server.gameRunning) {
                                            server.startGameProcedure();
                                        }
                                    }
                                }
                            }
                            break;
                        case "MOVE":
                            String[] coords = payload.split(",");
                            if (coords.length == 2) {
                                try {
                                    int row = Integer.parseInt(coords[0]);
                                    int col = Integer.parseInt(coords[1]);
                                    server.handlePlayerMove(symbol, row, col, this);
                                } catch (NumberFormatException e) { sendMessage("ERROR Неверный формат координат"); }
                            } else { sendMessage("ERROR Неверный формат команды MOVE"); }
                            break;
                        case "NEW_GAME_REQUEST":
                            server.requestNewGame(this);
                            break;
                        case "CHAT":
                            server.broadcastChatMessage(symbol, playerName, payload);
                            break;
                        case "DISCONNECT":
                            return;
                        default:
                            sendMessage("ERROR Неизвестная команда");
                    }
                }
            } catch (SocketException e) {
                if (server.running && socket != null && !socket.isClosed()) {
                     System.out.println("Соединение с клиентом " + symbol + " ("+playerName+") потеряно: " + e.getMessage());
                }
            } catch (IOException e) {
                if (server.running && socket != null && !socket.isClosed()) {
                    System.err.println("Ошибка чтения от клиента " + symbol + " ("+playerName+"): " + e.getMessage());
                }
            } finally {
                server.removeClient(this);
                closeConnection();
            }
        }

        public void sendMessage(String message) {
            if (out != null && socket != null && !socket.isClosed()) {
                out.println(message);
            }
        }

        public void closeConnection() {
            try { if (socket != null && !socket.isClosed()) socket.close(); }
            catch (IOException e) { /* None */ }
            out = null; in = null;
        }
    }
}