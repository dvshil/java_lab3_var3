package model;

import gui.RestaurantGUI;

import javax.swing.*;
import java.awt.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class Restaurant {
    private final BlockingQueue<Order> kitchenQueue;
    private final Map<String, Order> readyOrders;
    private final Map<String, String> orderToWaiter;
    private final RestaurantGUI gui;

    private final List<Waiter> waiters;
    private final List<Cook> cooks;
    private ScheduledExecutorService clientScheduler;
    private ExecutorService cookPool;
    private ExecutorService waiterPool;
    private ScheduledFuture<?> clientGenerationTask;
    private ScheduledFuture<?> loadScheduleTask;

    private final int maxQueueSize;
    private final AtomicInteger totalOrders = new AtomicInteger(0);
    private final AtomicInteger completedOrders = new AtomicInteger(0);
    private final AtomicInteger waitingOrders = new AtomicInteger(0);
    private final AtomicInteger cookingOrders = new AtomicInteger(0);
    private final AtomicInteger deliveredOrders = new AtomicInteger(0);

    private final AtomicInteger waiterIndex = new AtomicInteger(0);

    private volatile boolean isRunning = false;
    private volatile boolean isPaused = false;
    private volatile LoadMode currentLoadMode = LoadMode.NORMAL;
    private final Random random = new Random();

    private static final List<String> persistentHistory = Collections.synchronizedList(new ArrayList<>());
    private final List<String> sessionHistory = Collections.synchronizedList(new ArrayList<>());

    private final List<Order> visibleKitchenQueue = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, List<Order>> waiterQueues = new ConcurrentHashMap<>();

    private final Object pauseLock = new Object();
    private volatile boolean shouldStopOnPause = false;

    private static final int MAX_TOTAL_ORDERS = 100;

    private final Color MAIN_BG = new Color(248, 248, 250);
    private final Color ACCENT_BG = new Color(44, 47, 56);
    private final Color LIGHT_BG = new Color(255, 255, 255);
    private final Color TEXT_COLOR = new Color(60, 60, 60);
    private final Color BUTTON_STOP = new Color(133, 63, 68);
    private final Color BUTTON_ACTION = new Color(67, 117, 63);

    //нагрузка ресторана (к концу смена пик нагрузки, примерно в середине высокая, в остальное время обычная)
    private enum LoadMode {
        NORMAL("Нормальная", 800, 1500),
        HIGH("Высокая", 400, 800),
        PEAK("Пиковая", 200, 400);

        private final String name;
        private final int minDelay;
        private final int maxDelay;

        LoadMode(String name, int minDelay, int maxDelay) {
            this.name = name;
            this.minDelay = minDelay;
            this.maxDelay = maxDelay;
        }

        public String getName() { return name; }
        public int getDelay() {
            return minDelay + (int)(Math.random() * (maxDelay - minDelay));
        }
    }

    public Restaurant(RestaurantGUI gui, int waiterCount, int cookCount) {
        this.gui = gui;
        this.maxQueueSize = 20;

        this.kitchenQueue = new LinkedBlockingQueue<>(maxQueueSize);
        this.readyOrders = new ConcurrentHashMap<>();
        this.orderToWaiter = new ConcurrentHashMap<>();

        this.waiters = Collections.synchronizedList(new ArrayList<>());
        this.cooks = Collections.synchronizedList(new ArrayList<>());

        initializeStaff(waiterCount, cookCount);
    }

    private void initializeStaff(int waiterCount, int cookCount) {
        for (int i = 1; i <= waiterCount; i++) {
            Waiter waiter = new Waiter("Официант-" + i, this, gui);
            waiters.add(waiter);
            waiterQueues.put(waiter.getName(), new ArrayList<>());
        }

        int actualCookCount = cookCount;
        for (int i = 1; i <= actualCookCount; i++) {
            Cook cook = new Cook("Повар-" + i, this, gui);
            cooks.add(cook);
        }
    }

    public void start() {
        if (isRunning) {
            gui.logMessage("Ресторан уже работает!");
            return;
        }

        isRunning = true;
        isPaused = false;
        shouldStopOnPause = false;
        waiterIndex.set(0);

        totalOrders.set(0);
        waitingOrders.set(0);
        cookingOrders.set(0);
        deliveredOrders.set(0);
        completedOrders.set(0);

        visibleKitchenQueue.clear();
        waiterQueues.values().forEach(List::clear);
        readyOrders.clear();
        orderToWaiter.clear();

        sessionHistory.clear();
        sessionHistory.add("Ресторан начал работу: " + new Date());

        this.clientScheduler = Executors.newScheduledThreadPool(5);
        this.cookPool = Executors.newFixedThreadPool(cooks.size());
        this.waiterPool = Executors.newCachedThreadPool();

        gui.logMessage("=== РЕСТОРАН ОТКРЫЛСЯ ===");
        gui.logMessage("Смена продлится 3 минуты или до 100 заказов");
        gui.logMessage("Режим: " + currentLoadMode.getName() + " нагрузка");
        gui.logMessage("Очередь кухни: " + maxQueueSize + " мест");
        gui.logMessage("Поваров: " + cooks.size() + " (специально мало для очереди)");
        gui.logMessage("Лимит заказов: " + MAX_TOTAL_ORDERS);

        for (Cook cook : cooks) {
            cook.setCooking(true);
            cookPool.submit(cook);
        }

        for (Waiter waiter : waiters) {
            waiter.setWorking(true);
            waiterPool.submit(waiter);
        }

        startClientGeneration();

        startLoadSchedule();

        gui.updateStatistics();
        gui.updateQueueVisualization();
    }

    public void pause() {
        if (!isRunning) {
            gui.logMessage("Ресторан не работает!");
            return;
        }

        isPaused = !isPaused;

        if (isPaused) {
            gui.logMessage("=== ПАУЗА ===");
            gui.logMessage("Все процессы ПРИОСТАНОВЛЕНЫ");

            if (clientGenerationTask != null) {
                clientGenerationTask.cancel(false);
            }

            if (loadScheduleTask != null) {
                loadScheduleTask.cancel(false);
            }

            for (Waiter waiter : waiters) {
                waiter.setPaused(true);
            }

            for (Cook cook : cooks) {
                cook.setPaused(true);
            }

            shouldStopOnPause = true;

        } else {
            gui.logMessage("=== ПРОДОЛЖЕНИЕ РАБОТЫ ===");

            shouldStopOnPause = false;

            synchronized(pauseLock) {
                pauseLock.notifyAll();
            }

            for (Waiter waiter : waiters) {
                waiter.setPaused(true);
            }

            for (Cook cook : cooks) {
                cook.setPaused(true);
            }

            startClientGeneration();
            startLoadSchedule();
        }

        gui.updatePauseButton(isPaused);
        gui.updateStatistics();
    }

    public void checkPause() throws InterruptedException {
        if (shouldStopOnPause) {
            synchronized(pauseLock) {
                while (shouldStopOnPause && isRunning) {
                    pauseLock.wait(100);
                    if (shouldStopOnPause && isRunning) {
                        Thread.yield();
                    }
                }
            }
        }
    }

    //планировка нагрузки
    private void startLoadSchedule() {
        if (loadScheduleTask != null) {
            loadScheduleTask.cancel(false);
        }

        loadScheduleTask = clientScheduler.schedule(() -> {
            if (!isRunning || isPaused) return;
            setLoadMode(LoadMode.HIGH);
        }, 1 * 60, TimeUnit.SECONDS);

        clientScheduler.schedule(() -> {
            if (!isRunning || isPaused) return;
            setLoadMode(LoadMode.PEAK);
        }, 2 * 60, TimeUnit.SECONDS);
    }

    private void setLoadMode(LoadMode mode) {
        currentLoadMode = mode;
    }

    private void startClientGeneration() {
        if (clientGenerationTask != null) {
            clientGenerationTask.cancel(false);
        }

        String[] clientNames = {
                "Иван Иванов", "Мария Петрова", "Алексей Сидоров",
                "Екатерина Кузнецова", "Дмитрий Васильев", "Ольга Николаева",
                "Сергей Смирнов", "Анна Попова", "Павел Федоров"
        };

        clientGenerationTask = clientScheduler.scheduleAtFixedRate(() -> {
            try {
                if (!isRunning || isPaused) return;

                if (totalOrders.get() >= MAX_TOTAL_ORDERS) {
                    if (isRunning) {
                        gui.logMessage("Достигнут лимит в " + MAX_TOTAL_ORDERS + " заказов");
                        gui.logMessage("Генерация новых клиентов приостановлена");
                        stopClientGeneration();

                        showLimitReachedDialog();
                    }
                    return;
                }

                checkPause();

                int delay = currentLoadMode.getDelay();
                Thread.sleep(delay);

                if (!isRunning || isPaused) return;

                checkPause();

                String clientName = clientNames[random.nextInt(clientNames.length)];
                Order order = Order.createRandomOrder(clientName);
                int currentTotal = totalOrders.incrementAndGet();

                gui.logMessage("Клиент " + clientName + " заказал: " +
                        order.getDishName() + " (" + order.getDishCategory().getDisplayName() + ") " +
                        "[Всего: " + currentTotal + "/" + MAX_TOTAL_ORDERS + "]");

                assignOrderToWaiter(order);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

        }, 0, 100, TimeUnit.MILLISECONDS);
    }

    private void stopClientGeneration() {
        if (clientGenerationTask != null) {
            clientGenerationTask.cancel(false);
            clientGenerationTask = null;
        }
    }

    //установка лимита заказов в 100 штук, т.к. экономит память и без него уходит в бесконечность и зависает
    private void showLimitReachedDialog() {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((java.awt.Frame) null, "Лимит достигнут", true);
            dialog.setSize(500, 350);
            dialog.setLayout(new BorderLayout());
            dialog.setLocationRelativeTo(null);
            dialog.getContentPane().setBackground(MAIN_BG);

            JLabel titleLabel = new JLabel("ЛИМИТ ЗАКАЗОВ ДОСТИГНУТ", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            titleLabel.setForeground(TEXT_COLOR);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setBackground(MAIN_BG);
            titlePanel.add(titleLabel, BorderLayout.CENTER);

            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(LIGHT_BG);
            contentPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_BG, 1),
                    BorderFactory.createEmptyBorder(20, 20, 20, 20)
            ));

            JTextArea messageArea = new JTextArea();
            messageArea.setEditable(false);
            messageArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            messageArea.setBackground(LIGHT_BG);
            messageArea.setForeground(TEXT_COLOR);
            messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
            messageArea.setLineWrap(true);
            messageArea.setWrapStyleWord(true);

            StringBuilder message = new StringBuilder();
            message.append("Смена завершена по достижению лимита!\n\n");
            message.append("Достигнут максимальный лимит: ").append(MAX_TOTAL_ORDERS).append(" заказов\n\n");
            message.append("Итоги смены:\n");
            message.append("• Всего заказов: ").append(totalOrders.get()).append("\n");
            message.append("• Доставлено: ").append(deliveredOrders.get()).append("\n");
            message.append("• В ожидании: ").append(waitingOrders.get()).append("\n");
            message.append("• Готовятся: ").append(cookingOrders.get()).append("\n");
            message.append("• Готовы: ").append(readyOrders.size()).append("\n");
            message.append("• Очередь кухни: ").append(kitchenQueue.size()).append("/").append(maxQueueSize).append("\n\n");
            message.append("Новые заказы не принимаются.\n");
            message.append("Дождитесь завершения текущих заказов или завершите смену.");

            messageArea.setText(message.toString());

            JButton okButton = createStyledButton("ПРОДОЛЖИТЬ РАБОТУ", BUTTON_ACTION, dialog);
            okButton.addActionListener(e -> dialog.dispose());

            JButton stopButton = createStyledButton("ЗАВЕРШИТЬ СМЕНУ", BUTTON_STOP, dialog);
            stopButton.addActionListener(e -> {
                dialog.dispose();
                stop();
            });

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 20, 0));
            buttonPanel.setBackground(MAIN_BG);
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
            buttonPanel.add(okButton);
            buttonPanel.add(stopButton);

            contentPanel.add(new JScrollPane(messageArea), BorderLayout.CENTER);

            dialog.add(titlePanel, BorderLayout.NORTH);
            dialog.add(contentPanel, BorderLayout.CENTER);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            dialog.setVisible(true);
        });
    }

    public void assignOrderToWaiter(Order order) {
        waitingOrders.incrementAndGet();
        gui.updateQueueVisualization();

        if (waiters.isEmpty()) {
            gui.logMessage("Нет доступных официантов!");
            addToWaiterQueue("Ожидание", order);
            gui.updateStatistics();
            return;
        }

        List<Waiter> workingWaiters = waiters.stream()
                .filter(Waiter::isWorking)
                .collect(java.util.stream.Collectors.toList());

        if (workingWaiters.isEmpty()) {
            gui.logMessage("Все официанты не работают! Клиент " + order.getClientName() + " ждет...");
            addToWaiterQueue("Ожидание", order);
            gui.updateStatistics();
            return;
        }

        //выбираем менее загруженного
        Waiter selectedWaiter = null;
        int minLoad = Integer.MAX_VALUE;

        for (Waiter waiter : workingWaiters) {
            int currentLoad = waiter.getCurrentOrders();
            if (currentLoad < waiter.getMaxConcurrentOrders() && currentLoad < minLoad) {
                selectedWaiter = waiter;
                minLoad = currentLoad;
            }
        }

        if (selectedWaiter != null) {
            selectedWaiter.acceptOrder(order);
            gui.logMessage("Заказ " + order.getId() + " назначен " + selectedWaiter.getName() +
                    " (активных: " + selectedWaiter.getCurrentOrders() + ", загруженность: " +
                    selectedWaiter.getLoadPercentage() + "%)");

            addToWaiterQueue(selectedWaiter.getName(), order);
            gui.updateStatistics();
        } else {
            gui.logMessage("Все официанты заняты! Заказ " + order.getId() + " ждет в общей очереди");
            addToWaiterQueue("Общая очередь", order);
            gui.updateStatistics();
        }
    }

    private void addToWaiterQueue(String waiterName, Order order) {
        List<Order> queue = waiterQueues.get(waiterName);
        if (queue == null) {
            queue = Collections.synchronizedList(new ArrayList<>());
            waiterQueues.put(waiterName, queue);
        }
        synchronized(queue) {
            if (queue.size() < 20) {
                queue.add(order);
            }
        }
        gui.updateQueueVisualization();
    }

    private void removeFromWaiterQueue(String waiterName, String orderId) {
        List<Order> queue = waiterQueues.get(waiterName);
        if (queue != null) {
            synchronized(queue) {
                queue.removeIf(order -> order.getId().equals(orderId));
            }
            gui.updateQueueVisualization();
        }
    }

    public void addManualOrder(Order order) {
        if (!isRunning) {
            gui.logMessage("Ресторан не работает! Заказ не может быть принят.");
            return;
        }

        if (isPaused) {
            gui.logMessage("Ресторан на паузе! Заказ не может быть принят.");
            return;
        }

        if (totalOrders.get() >= MAX_TOTAL_ORDERS) {
            gui.logMessage("Достигнут лимит в " + MAX_TOTAL_ORDERS + " заказов!");
            gui.logMessage("Новые заказы не принимаются.");

            SwingUtilities.invokeLater(() -> {
                JOptionPane optionPane = new JOptionPane(
                        "Достигнут лимит в " + MAX_TOTAL_ORDERS + " заказов!\n" +
                                "Новые заказы не принимаются.",
                        JOptionPane.WARNING_MESSAGE
                );
                JDialog dialog = optionPane.createDialog("Лимит достигнут");
                dialog.getContentPane().setBackground(MAIN_BG);
                dialog.setVisible(true);
            });
            return;
        }

        int currentTotal = totalOrders.incrementAndGet();

        gui.logMessage("Вручную добавлен заказ: " + order.getDishName() +
                " для " + order.getClientName() +
                " [Всего: " + currentTotal + "/" + MAX_TOTAL_ORDERS + "]");

        assignOrderToWaiter(order);
    }

    //работа с очередью, сначала заказ в очередь
    public boolean addToKitchenQueue(Order order, String waiterName) {
        try {
            checkPause();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }

        if (!isRunning || isPaused) {
            gui.logMessage("Ресторан не работает или на паузе! Заказ " + order.getId() + " ждет...");
            return false;
        }

        removeFromWaiterQueue(waiterName, order.getId());

        if (kitchenQueue.size() >= maxQueueSize) {
            int queueSize = kitchenQueue.size();
            gui.logMessage("Очередь на кухне ПЕРЕПОЛНЕНА! (" + queueSize + "/" + maxQueueSize + ")");

            int waitCount = 0;
            while (kitchenQueue.size() >= maxQueueSize && isRunning && !isPaused && waitCount < 5) {
                try {
                    checkPause();
                    Thread.sleep(1000);
                    waitCount++;

                    if (kitchenQueue.size() >= maxQueueSize) {
                        gui.logMessage("Ожидание в очереди... (" + waitCount + " сек)");
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return false;
                }
            }

            if (!isRunning || isPaused) {
                return false;
            }
        }

        try {
            orderToWaiter.put(order.getId(), waiterName);
            kitchenQueue.put(order);

            synchronized(visibleKitchenQueue) {
                if (visibleKitchenQueue.size() < 50) {
                    visibleKitchenQueue.add(order);
                }
            }

            order.setStatus(Order.OrderStatus.WAITING_FOR_COOKING);
            order.setAssignedWaiter(waiterName);

            waitingOrders.decrementAndGet();
            cookingOrders.incrementAndGet();

            gui.updateQueueStatus(kitchenQueue.size(), maxQueueSize);
            gui.updateOrderStatus(order);
            gui.updateStatistics();
            gui.updateQueueVisualization();

            int queueSize = kitchenQueue.size();
            int fillPercentage = queueSize * 100 / maxQueueSize;
            String status;
            if (queueSize >= maxQueueSize) {
                status = "ПЕРЕПОЛНЕНА!";
            } else if (queueSize > maxQueueSize * 0.7) {
                status = "Высокая загрузка";
            } else if (queueSize > maxQueueSize * 0.4) {
                status = "Средняя загрузка";
            } else {
                status = "Низкая загрузка";
            }

            gui.logMessage("Очередь на кухне: " + status + " (" + queueSize + "/" + maxQueueSize + ", " + fillPercentage + "%)");

            gui.logMessage("Заказ " + order.getId() + " добавлен в очередь кухни");

            return true;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            gui.logMessage("Добавление заказа в очередь прервано: " + order.getId());
            waitingOrders.decrementAndGet();
            return false;
        }
    }

    //забираем из очереди
    public Order takeFromKitchenQueue() throws InterruptedException {
        checkPause();

        Order order = kitchenQueue.take();

        synchronized(visibleKitchenQueue) {
            visibleKitchenQueue.remove(order);
        }

        gui.updateQueueStatus(kitchenQueue.size(), maxQueueSize);
        gui.updateQueueVisualization();

        int queueSize = kitchenQueue.size();
        if (queueSize > 0) {
            gui.logMessage("Повар взял заказ из очереди. Осталось: " + queueSize + "/" + maxQueueSize);
        }

        return order;
    }

    //готовим
    public void completeOrder(Order order) {
        if (order == null || !isRunning) return;

        order.setStatus(Order.OrderStatus.READY);
        readyOrders.put(order.getId(), order);

        cookingOrders.decrementAndGet();

        gui.updateOrderStatus(order);
        gui.updateStatistics();
        gui.updateQueueVisualization();

        String waiterName = orderToWaiter.get(order.getId());
        if (waiterName != null) {
            gui.logMessage("Заказ " + order.getId() + " готов! " + waiterName + " может забрать");
        }
    }

    //официант забирает и доставляет
    public Order takeReadyOrder(String waiterName) {
        if (!isRunning || isPaused) return null;

        for (Map.Entry<String, String> entry : orderToWaiter.entrySet()) {
            if (entry.getValue().equals(waiterName)) {
                String orderId = entry.getKey();
                Order order = readyOrders.get(orderId);
                if (order != null && order.getStatus() == Order.OrderStatus.READY) {
                    return order;
                }
            }
        }
        return null;
    }

    public boolean markOrderAsTaken(String orderId) {
        Order order = readyOrders.remove(orderId);
        if (order != null) {
            orderToWaiter.remove(orderId);
            return true;
        }
        return false;
    }

    public void deliverOrder(Order order) {
        if (order == null || !isRunning) return;

        order.setStatus(Order.OrderStatus.DELIVERED);
        completedOrders.incrementAndGet();
        deliveredOrders.incrementAndGet();

        orderToWaiter.remove(order.getId());
        readyOrders.remove(order.getId());

        gui.logMessage(order.getAssignedWaiter() + " доставил " +
                order.getDishName() + " клиенту " + order.getClientName() +
                " [Доставлено: " + deliveredOrders.get() + "]");
        gui.updateOrderStatus(order);
        gui.updateStatistics();
        gui.updateQueueVisualization();

        if (deliveredOrders.get() % 20 == 0) {
            gui.removeOldOrders();
        }
    }

    public void stop() {
        if (!isRunning) return;

        isRunning = false;
        isPaused = false;
        shouldStopOnPause = false;

        synchronized(pauseLock) {
            pauseLock.notifyAll();
        }

        gui.logMessage("Завершение работы... Очистка ресурсов");

        stopClientGeneration();

        kitchenQueue.clear();
        visibleKitchenQueue.clear();
        readyOrders.clear();
        orderToWaiter.clear();
        waiterQueues.clear();
        sessionHistory.clear();

        synchronized(persistentHistory) {
            persistentHistory.add("=== НАЧАЛО СМЕНЫ ===");
            persistentHistory.add("Итоги смены:");
            persistentHistory.add("Всего заказов: " + totalOrders.get());
            persistentHistory.add("Доставлено заказов: " + deliveredOrders.get());
            persistentHistory.add("В ожидании: " + waitingOrders.get());
            persistentHistory.add("Готовятся: " + cookingOrders.get());
            persistentHistory.add("Готовы: " + readyOrders.size());
            persistentHistory.add("Очередь кухни: " + kitchenQueue.size() + "/" + maxQueueSize);
            persistentHistory.add("=== КОНЕЦ СМЕНЫ ===");
            persistentHistory.add("");
        }

        if (clientGenerationTask != null) {
            clientGenerationTask.cancel(true);
        }

        if (loadScheduleTask != null) {
            loadScheduleTask.cancel(true);
        }

        if (clientScheduler != null) {
            clientScheduler.shutdownNow();
            try {
                clientScheduler.awaitTermination(1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        for (Cook cook : cooks) {
            cook.setCooking(false);
            cook.setPaused(false);
        }

        for (Waiter waiter : waiters) {
            waiter.setWorking(false);
            waiter.setPaused(false);
        }

        if (cookPool != null) {
            cookPool.shutdown();
            try {
                if (!cookPool.awaitTermination(2, TimeUnit.SECONDS)) {
                    cookPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                cookPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        if (waiterPool != null) {
            waiterPool.shutdown();
            try {
                if (!waiterPool.awaitTermination(3, TimeUnit.SECONDS)) {
                    waiterPool.shutdownNow();
                }
            } catch (InterruptedException e) {
                waiterPool.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        gui.clearActiveOrders();
        gui.updateQueueVisualization();

        showCompletionDialog();
        showHistory();
    }

    public void showCompletionDialog() {
        SwingUtilities.invokeLater(() -> {
            JDialog dialog = new JDialog((java.awt.Frame) null, "Смена завершена", true);
            dialog.setSize(400, 350);
            dialog.setLayout(new BorderLayout());
            dialog.getContentPane().setBackground(MAIN_BG);
            dialog.setLocationRelativeTo(null);

            JLabel titleLabel = new JLabel("СМЕНА ЗАВЕРШЕНА", SwingConstants.CENTER);
            titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 18));
            titleLabel.setForeground(TEXT_COLOR);
            titleLabel.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

            JPanel titlePanel = new JPanel(new BorderLayout());
            titlePanel.setBackground(MAIN_BG);
            titlePanel.add(titleLabel, BorderLayout.CENTER);

            JPanel contentPanel = new JPanel(new BorderLayout());
            contentPanel.setBackground(LIGHT_BG);
            contentPanel.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(ACCENT_BG, 1),
                    BorderFactory.createEmptyBorder(20, 20, 20, 20)
            ));

            JTextArea statsArea = new JTextArea();
            statsArea.setEditable(false);
            statsArea.setFont(new Font("Segoe UI", Font.PLAIN, 13));
            statsArea.setBackground(LIGHT_BG);
            statsArea.setForeground(TEXT_COLOR);
            statsArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            StringBuilder stats = new StringBuilder();
            stats.append("ИТОГИ СМЕНЫ:\n\n");
            stats.append("Всего заказов: ").append(totalOrders.get()).append("\n");
            stats.append("Доставлено: ").append(deliveredOrders.get()).append("\n");
            stats.append("В ожидании: ").append(waitingOrders.get()).append("\n");
            stats.append("Готовятся: ").append(cookingOrders.get()).append("\n");
            stats.append("Готовы: ").append(readyOrders.size()).append("\n");
            stats.append("\nОчередь кухни: ").append(kitchenQueue.size()).append("/").append(maxQueueSize);
            stats.append("\n\nЛимит заказов: ").append(totalOrders.get()).append("/").append(MAX_TOTAL_ORDERS);

            statsArea.setText(stats.toString());

            JButton okButton = createStyledButton("ОК", BUTTON_ACTION, dialog);
            okButton.addActionListener(e -> dialog.dispose());

            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER));
            buttonPanel.setBackground(MAIN_BG);
            buttonPanel.setBorder(BorderFactory.createEmptyBorder(15, 0, 0, 0));
            buttonPanel.add(okButton);

            contentPanel.add(new JScrollPane(statsArea), BorderLayout.CENTER);

            dialog.add(titlePanel, BorderLayout.NORTH);
            dialog.add(contentPanel, BorderLayout.CENTER);
            dialog.add(buttonPanel, BorderLayout.SOUTH);
            dialog.setVisible(true);
        });
    }

    private JButton createStyledButton(String text, Color bgColor, JDialog dialog) {
        JButton button = new JButton(text) {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (getModel().isPressed()) {
                    g2.setColor(bgColor.darker());
                } else if (getModel().isRollover()) {
                    g2.setColor(bgColor.brighter());
                } else {
                    g2.setColor(bgColor);
                }

                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 20, 20);
                g2.dispose();
                super.paintComponent(g);
            }

            @Override
            protected void paintBorder(Graphics g) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(bgColor.darker().darker());
                g2.setStroke(new BasicStroke(2));
                g2.drawRoundRect(1, 1, getWidth() - 2, getHeight() - 2, 20, 20);
                g2.dispose();
            }
        };

        button.setFont(new Font("Segoe UI", Font.BOLD, 12));
        button.setForeground(Color.WHITE);
        button.setBackground(bgColor);
        button.setBorder(BorderFactory.createEmptyBorder(10, 25, 10, 25));
        button.setFocusPainted(false);
        button.setCursor(new Cursor(Cursor.HAND_CURSOR));
        button.setOpaque(false);
        button.setContentAreaFilled(false);

        button.addMouseListener(new java.awt.event.MouseAdapter() {
            public void mouseEntered(java.awt.event.MouseEvent evt) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }

            public void mouseExited(java.awt.event.MouseEvent evt) {
                button.setForeground(Color.WHITE);
                button.repaint();
            }
        });

        return button;
    }

    private void showHistory() {
        gui.logMessage("=== ИСТОРИЯ РАБОТЫ ===");
        synchronized(persistentHistory) {
            for (String record : persistentHistory) {
                gui.logMessage(record);
            }
        }
        gui.logMessage("=== КОНЕЦ ИСТОРИИ ===");
    }

    public List<String> getPersistentHistory() {
        synchronized(persistentHistory) {
            return new ArrayList<>(persistentHistory);
        }
    }

    public void clearPersistentHistory() {
        synchronized(persistentHistory) {
            persistentHistory.clear();
        }
    }

    public List<Order> getVisibleKitchenQueue() {
        synchronized(visibleKitchenQueue) {
            return new ArrayList<>(visibleKitchenQueue);
        }
    }

    public Map<String, List<Order>> getWaiterQueues() {
        Map<String, List<Order>> copy = new HashMap<>();
        for (Map.Entry<String, List<Order>> entry : waiterQueues.entrySet()) {
            synchronized(entry.getValue()) {
                if (entry.getValue().size() > 0) {
                    copy.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
        }
        return copy;
    }

    public boolean isPaused() {
        return isPaused;
    }

    public int getQueueSize() { return kitchenQueue.size(); }
    public int getMaxQueueSize() { return maxQueueSize; }
    public int getReadyOrdersCount() { return readyOrders.size(); }
    public int getTotalOrders() { return totalOrders.get(); }
    public int getWaitingOrders() { return waitingOrders.get(); }
    public int getCookingOrders() { return cookingOrders.get(); }
    public int getDeliveredOrders() { return deliveredOrders.get(); }
    public List<Waiter> getWaiters() {
        synchronized(waiters) {
            return new ArrayList<>(waiters);
        }
    }
    public List<Cook> getCooks() {
        synchronized(cooks) {
            return new ArrayList<>(cooks);
        }
    }
    public boolean isRunning() { return isRunning; }
}