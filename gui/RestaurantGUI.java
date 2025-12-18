package gui;

import model.*;
import javax.swing.*;
import javax.swing.border.*;
import javax.swing.table.*;
import java.awt.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.Timer;
import java.util.concurrent.atomic.AtomicBoolean;

public class RestaurantGUI extends JFrame {
    private final Restaurant restaurant;

    private JTextArea logArea;
    private JTable ordersTable;
    private DefaultTableModel ordersTableModel;
    private JTable waitersTable;
    private DefaultTableModel waitersTableModel;
    private JTable cooksTable;
    private DefaultTableModel cooksTableModel;

    private JLabel totalOrdersLabel;
    private JLabel waitingOrdersLabel;
    private JLabel cookingOrdersLabel;
    private JLabel readyOrdersLabel;
    private JLabel deliveredOrdersLabel;
    private JLabel timeLabel;

    private JProgressBar queueProgressBar;

    private JTextArea kitchenQueueArea;
    private JTextArea waiterQueueArea;

    private final Map<String, Order> activeOrders = Collections.synchronizedMap(
            new LinkedHashMap<>() {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Order> eldest) {
                    return size() > 100;
                }
            }
    );

    private JComboBox<String> dishComboInDialog;

    private JButton startButton;
    private JButton pauseButton;
    private JButton stopButton;

    private final Color BUTTON_COLOR = new Color(30, 33, 41);
    private final Color TEXT_COLOR = new Color(240, 240, 240);
    private final Color ACCENT_BG = new Color(44, 47, 56);
    private final Color MAIN_BG = new Color(248, 248, 250);
    private final Color LIGHT_BG = new Color(255, 255, 255);

    private final Color BUTTON_START = new Color(67, 117, 63);
    private final Color BUTTON_PAUSE = new Color(149, 125, 83);
    private final Color BUTTON_STOP = new Color(133, 63, 68);
    private final Color BUTTON_HISTORY = new Color(120, 143, 165);
    private final Color BUTTON_ADD = new Color(145, 108, 152);

    private final Color STATUS_CREATED = new Color(214, 218, 230);
    private final Color STATUS_WAITING = new Color(169, 206, 217);
    private final Color STATUS_COOKING = new Color(138, 177, 191);
    private final Color STATUS_READY = new Color(168, 227, 128);
    private final Color STATUS_DELIVERED = new Color(117, 211, 96);

    private final Map<Order.DishCategory, String[]> categoryDishesMap = new EnumMap<>(Order.DishCategory.class);

    private final AtomicBoolean updateScheduled = new AtomicBoolean(false);
    private final AtomicBoolean queueUpdateScheduled = new AtomicBoolean(false);

    public RestaurantGUI() {
        setTitle("Система работы ресторана");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(1400, 900);
        setLayout(new BorderLayout());

        restaurant = new Restaurant(this, 3, 2);

        initializeDishesMap();

        initUI();
        startClock();
    }

    private void initializeDishesMap() {
        for (Order.DishCategory category : Order.DishCategory.values()) {
            categoryDishesMap.put(category, category.getDishNamesForDisplay());
        }
    }

    private void initUI() {
        JPanel mainPanel = new JPanel(new BorderLayout());
        mainPanel.setBackground(MAIN_BG);
        add(mainPanel);

        mainPanel.add(createHeaderPanel(), BorderLayout.NORTH);

        JTabbedPane tabbedPane = createTabbedPane();
        mainPanel.add(tabbedPane, BorderLayout.CENTER);

        mainPanel.add(createFooterPanel(), BorderLayout.SOUTH);
    }

    private JPanel createHeaderPanel() {
        JPanel header = new JPanel(new BorderLayout());
        header.setBackground(ACCENT_BG);
        header.setBorder(BorderFactory.createEmptyBorder(15, 20, 15, 20));

        JLabel title = new JLabel("СИСТЕМА РАБОТЫ РЕСТОРАНА");
        title.setFont(new Font("Segoe UI", Font.BOLD, 24));
        title.setForeground(TEXT_COLOR);

        JPanel controlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        controlPanel.setOpaque(false);

        startButton = createStyledButton("ЗАПУСК", BUTTON_START);
        startButton.addActionListener(e -> restaurant.start());

        pauseButton = createStyledButton("ПАУЗА", BUTTON_PAUSE);
        pauseButton.addActionListener(e -> restaurant.pause());
        pauseButton.setEnabled(false);

        stopButton = createStyledButton("ЗАВЕРШИТЬ", BUTTON_STOP);
        stopButton.addActionListener(e -> restaurant.stop());
        stopButton.setEnabled(false);

        JButton historyButton = createStyledButton("ИСТОРИЯ", BUTTON_HISTORY);
        historyButton.addActionListener(e -> showHistoryDialog());

        JButton addOrderButton = createStyledButton("ДОБАВИТЬ ЗАКАЗ", BUTTON_ADD);
        addOrderButton.addActionListener(e -> showAddOrderDialog());

        controlPanel.add(startButton);
        controlPanel.add(pauseButton);
        controlPanel.add(stopButton);
        controlPanel.add(historyButton);
        controlPanel.add(addOrderButton);

        header.add(title, BorderLayout.WEST);
        header.add(controlPanel, BorderLayout.EAST);

        return header;
    }

    public void updatePauseButton(boolean isPaused) {
        SwingUtilities.invokeLater(() -> {
            if (isPaused) {
                pauseButton.setText("ПРОДОЛЖИТЬ");
                pauseButton.setBackground(BUTTON_PAUSE.brighter());
            } else {
                pauseButton.setText("ПАУЗА");
                pauseButton.setBackground(BUTTON_PAUSE);
            }
        });
    }

    private JButton createStyledButton(String text, Color bgColor) {
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
        button.setBorder(BorderFactory.createEmptyBorder(12, 25, 12, 25));
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

    private void showHistoryDialog() {
        JDialog dialog = new JDialog(this, "История всех смен", true);
        dialog.setSize(600, 500);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(MAIN_BG);
        dialog.setLocationRelativeTo(this);

        JTextArea historyArea = new JTextArea();
        historyArea.setEditable(false);
        historyArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        historyArea.setBackground(LIGHT_BG);
        historyArea.setForeground(new Color(60, 60, 60));

        List<String> history = restaurant.getPersistentHistory();
        StringBuilder historyText = new StringBuilder();
        for (String record : history) {
            historyText.append(record).append("\n");
        }

        if (historyText.length() == 0) {
            historyText.append("История смен пуста\n");
        }

        historyArea.setText(historyText.toString());

        JScrollPane scrollPane = new JScrollPane(historyArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(BUTTON_COLOR, 1));

        JButton clearButton = createStyledButton("Очистить историю", BUTTON_COLOR);
        clearButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(dialog,
                    "Вы уверены, что хотите очистить всю историю?",
                    "Подтверждение",
                    JOptionPane.YES_NO_OPTION);
            if (result == JOptionPane.YES_OPTION) {
                restaurant.clearPersistentHistory();
                historyArea.setText("История смен очищена\n");
            }
        });

        JButton closeButton = createStyledButton("Закрыть", BUTTON_COLOR);
        closeButton.addActionListener(e -> dialog.dispose());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttonPanel.setBackground(MAIN_BG);
        buttonPanel.add(clearButton);
        buttonPanel.add(closeButton);

        JLabel titleLabel = new JLabel("Полная история работы ресторана:", SwingConstants.CENTER);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(60, 60, 60));

        dialog.add(titleLabel, BorderLayout.NORTH);
        dialog.add(scrollPane, BorderLayout.CENTER);
        dialog.add(buttonPanel, BorderLayout.SOUTH);

        dialog.setVisible(true);
    }

    private JTabbedPane createTabbedPane() {
        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        tabbedPane.setBackground(MAIN_BG);
        tabbedPane.setForeground(new Color(60, 60, 60));
        tabbedPane.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));

        tabbedPane.setUI(new javax.swing.plaf.basic.BasicTabbedPaneUI() {
            @Override
            protected void paintTabBackground(Graphics g, int tabPlacement, int tabIndex,
                                              int x, int y, int w, int h, boolean isSelected) {
                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                if (isSelected) {
                    g2.setColor(LIGHT_BG);
                    g2.fillRoundRect(x + 1, y + 1, w - 2, h - 1, 10, 10);
                } else {
                    g2.setColor(MAIN_BG);
                }
                g2.dispose();
            }

            @Override
            protected void paintTabBorder(Graphics g, int tabPlacement, int tabIndex,
                                          int x, int y, int w, int h, boolean isSelected) {
                if (isSelected) {
                    Graphics2D g2 = (Graphics2D) g.create();
                    g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                    g2.setColor(ACCENT_BG);
                    g2.setStroke(new BasicStroke(2));
                    g2.drawRoundRect(x + 1, y + 1, w - 2, h - 1, 10, 10);
                    g2.dispose();
                }
            }

            @Override
            protected void paintContentBorder(Graphics g, int tabPlacement, int selectedIndex) {
                int width = tabPane.getWidth();
                int height = tabPane.getHeight();
                int x = 0;
                int y = 0;

                Graphics2D g2 = (Graphics2D) g.create();
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g2.setColor(ACCENT_BG);
                g2.drawRoundRect(x, y, width - 1, height - 1, 15, 15);
                g2.dispose();
            }
        });

        tabbedPane.addTab("АКТИВНЫЕ ЗАКАЗЫ", createOrdersPanel());
        tabbedPane.addTab("ПЕРСОНАЛ", createStaffPanel());
        tabbedPane.addTab("СТАТИСТИКА", createStatisticsPanel());
        tabbedPane.addTab("ОЧЕРЕДИ", createQueuesPanel());
        tabbedPane.addTab("ЖУРНАЛ СОБЫТИЙ", createLogPanel());

        return tabbedPane;
    }

    private JPanel createQueuesPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 10, 10));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JPanel kitchenQueuePanel = createQueuePanel("ОЧЕРЕДЬ НА КУХНЕ",
                "Здесь отображаются заказы, ожидающие приготовления. Очередь большая (20 мест) и будет заполняться:");
        kitchenQueueArea = (JTextArea) ((JScrollPane) kitchenQueuePanel.getComponent(1)).getViewport().getView();

        JPanel waiterQueuePanel = createQueuePanel("ОЧЕРЕДИ ОФИЦИАНТОВ",
                "Здесь отображаются заказы, которые принимают официанты:");
        waiterQueueArea = (JTextArea) ((JScrollPane) waiterQueuePanel.getComponent(1)).getViewport().getView();

        panel.add(kitchenQueuePanel);
        panel.add(waiterQueuePanel);

        return panel;
    }

    private JPanel createQueuePanel(String title, String description) {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_BG, 2),
                BorderFactory.createEmptyBorder(10, 10, 10, 10)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 16));
        titleLabel.setForeground(new Color(60, 60, 60));
        titleLabel.setBorder(BorderFactory.createEmptyBorder(0, 0, 10, 0));

        JLabel descLabel = new JLabel(description);
        descLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        descLabel.setForeground(new Color(100, 100, 100));

        JPanel headerPanel = new JPanel(new BorderLayout());
        headerPanel.setBackground(LIGHT_BG);
        headerPanel.add(titleLabel, BorderLayout.NORTH);
        headerPanel.add(descLabel, BorderLayout.SOUTH);

        JTextArea queueArea = new JTextArea();
        queueArea.setEditable(false);
        queueArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        queueArea.setBackground(LIGHT_BG);
        queueArea.setForeground(new Color(60, 60, 60));
        queueArea.setLineWrap(true);
        queueArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(queueArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));

        panel.add(headerPanel, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    public void updateQueueVisualization() {
        SwingUtilities.invokeLater(() -> {
            if (kitchenQueueArea == null || waiterQueueArea == null) return;

            if (queueUpdateScheduled.get()) {
                return;
            }

            queueUpdateScheduled.set(true);

            try {
                List<Order> kitchenQueue = restaurant.getVisibleKitchenQueue();
                StringBuilder kitchenText = new StringBuilder();
                kitchenText.append("Заказов в очереди: ").append(kitchenQueue.size()).append("/").append(restaurant.getMaxQueueSize()).append("\n");
                kitchenText.append("Статус: ");

                if (kitchenQueue.isEmpty()) {
                    kitchenText.append("Очередь пуста\n");
                } else if (kitchenQueue.size() >= restaurant.getMaxQueueSize()) {
                    kitchenText.append("ПЕРЕПОЛНЕНА!\n");
                }

                kitchenText.append("\n");

                if (kitchenQueue.isEmpty()) {
                    kitchenText.append("Нет заказов в очереди\n");
                } else {
                    int limit = Math.min(kitchenQueue.size(), 20);
                    for (int i = 0; i < limit; i++) {
                        Order order = kitchenQueue.get(i);
                        kitchenText.append(String.format("%2d. %s (%s) - %s\n",
                                i + 1,
                                order.getDishName(),
                                order.getDishCategory().getDisplayName(),
                                order.getClientName()));
                    }
                    if (kitchenQueue.size() > 20) {
                        kitchenText.append("... и еще ").append(kitchenQueue.size() - 20).append(" заказов\n");
                    }
                }
                kitchenQueueArea.setText(kitchenText.toString());

                Map<String, List<Order>> waiterQueues = restaurant.getWaiterQueues();
                StringBuilder waiterText = new StringBuilder();

                for (Map.Entry<String, List<Order>> entry : waiterQueues.entrySet()) {
                    List<Order> orders = entry.getValue();
                    waiterText.append(entry.getKey()).append(": ").append(orders.size()).append(" заказ(ов)\n");

                    if (!orders.isEmpty()) {
                        int limit = Math.min(orders.size(), 10);
                        for (int i = 0; i < limit; i++) {
                            Order order = orders.get(i);
                            waiterText.append(String.format("   %d. %s - %s\n",
                                    i + 1,
                                    order.getDishName(),
                                    order.getClientName()));
                        }
                        if (orders.size() > 10) {
                            waiterText.append("   ... и еще ").append(orders.size() - 10).append(" заказов\n");
                        }
                    } else {
                        waiterText.append("   (нет заказов)\n");
                    }
                    waiterText.append("\n");
                }
                waiterQueueArea.setText(waiterText.toString());
            } finally {
                queueUpdateScheduled.set(false);
            }
        });
    }

    private JPanel createOrdersPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Активные заказы");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(60, 60, 60));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        String[] columns = {
                "№", "Клиент", "Блюдо", "Категория",
                "Статус", "Официант", "Время", "Прогресс"
        };

        ordersTableModel = new DefaultTableModel(columns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }

            @Override
            public Class<?> getColumnClass(int columnIndex) {
                if (columnIndex == 7) return JProgressBar.class;
                return String.class;
            }
        };

        ordersTable = new JTable(ordersTableModel);
        styleOrdersTable();

        JScrollPane scrollPane = new JScrollPane(ordersTable);
        scrollPane.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));

        panel.add(title, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);

        return panel;
    }

    private void styleOrdersTable() {
        ordersTable.setRowHeight(40);
        ordersTable.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        ordersTable.setForeground(new Color(60, 60, 60));
        ordersTable.setBackground(ACCENT_BG);
        ordersTable.setSelectionBackground(new Color(176, 194, 213));
        ordersTable.setSelectionForeground(new Color(60, 60, 60));

        JTableHeader header = ordersTable.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 12));
        header.setBackground(ACCENT_BG);
        header.setForeground(ACCENT_BG);
        ordersTable.setGridColor(MAIN_BG.darker());

        ordersTable.getColumnModel().getColumn(0).setPreferredWidth(40);
        ordersTable.getColumnModel().getColumn(1).setPreferredWidth(120);
        ordersTable.getColumnModel().getColumn(2).setPreferredWidth(180);
        ordersTable.getColumnModel().getColumn(3).setPreferredWidth(100);
        ordersTable.getColumnModel().getColumn(4).setPreferredWidth(120);
        ordersTable.getColumnModel().getColumn(5).setPreferredWidth(100);
        ordersTable.getColumnModel().getColumn(6).setPreferredWidth(80);
        ordersTable.getColumnModel().getColumn(7).setPreferredWidth(200);

        ordersTable.getColumnModel().getColumn(7).setCellRenderer(new ProgressBarRenderer());

        ordersTable.setDefaultRenderer(Object.class, new StatusColorRenderer());
    }

    private static class ProgressBarRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            if (value instanceof JProgressBar) {
                JProgressBar pb = (JProgressBar) value;
                pb.setBorderPainted(false);
                pb.setOpaque(true);
                return pb;
            }

            return super.getTableCellRendererComponent(table, value, isSelected,
                    hasFocus, row, column);
        }
    }

    private class StatusColorRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable table, Object value,
                                                       boolean isSelected, boolean hasFocus, int row, int column) {

            Component c = super.getTableCellRendererComponent(table, value,
                    isSelected, hasFocus, row, column);

            if (row == 0 && !table.getTableHeader().getBounds().contains(0, row)) {
                c.setBackground(ACCENT_BG);
                c.setForeground(TEXT_COLOR);
                return c;
            }

            String status = (String) table.getValueAt(row, 4);

            if (status != null) {
                if (status.contains("Создан")) {
                    c.setBackground(STATUS_CREATED);
                } else if (status.contains("ожидании")) {
                    c.setBackground(STATUS_WAITING);
                } else if (status.contains("Готовится")) {
                    c.setBackground(STATUS_COOKING);
                } else if (status.contains("Готов")) {
                    c.setBackground(STATUS_READY);
                } else if (status.contains("Доставлен")) {
                    c.setBackground(STATUS_DELIVERED);
                } else {
                    c.setBackground(LIGHT_BG);
                }

                c.setForeground(new Color(60, 60, 60));

                if (isSelected) {
                    c.setBackground(c.getBackground().darker());
                }
            }

            return c;
        }
    }

    private JPanel createStaffPanel() {
        JPanel panel = new JPanel(new GridLayout(1, 2, 20, 0));
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        String[] waiterColumns = {"Имя", "Статус", "Заказов", "Продуктивность", "Загруженность"};
        waitersTableModel = new DefaultTableModel(waiterColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        waitersTable = new JTable(waitersTableModel);
        styleStaffTable(waitersTable);

        String[] cookColumns = {"Имя", "Статус", "Приготовлено", "Продуктивность"};
        cooksTableModel = new DefaultTableModel(cookColumns, 0) {
            @Override
            public boolean isCellEditable(int row, int column) {
                return false;
            }
        };
        cooksTable = new JTable(cooksTableModel);
        styleStaffTable(cooksTable);

        JScrollPane waiterScroll = createStyledScrollPane(waitersTable, "ОФИЦИАНТЫ");
        JScrollPane cookScroll = createStyledScrollPane(cooksTable, "ПОВАРА");

        panel.add(waiterScroll);
        panel.add(cookScroll);

        return panel;
    }

    private void styleStaffTable(JTable table) {
        table.setRowHeight(35);
        table.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        table.setForeground(new Color(60, 60, 60));
        table.setBackground(LIGHT_BG);
        table.setSelectionBackground(new Color(200, 220, 240));
        table.setSelectionForeground(new Color(60, 60, 60));
        JTableHeader header = table.getTableHeader();
        header.setFont(new Font("Segoe UI", Font.BOLD, 13));
        header.setBackground(ACCENT_BG);
        header.setForeground(ACCENT_BG);
        table.setGridColor(MAIN_BG.darker());
    }

    private JScrollPane createStyledScrollPane(JTable table, String title) {
        JScrollPane scrollPane = new JScrollPane(table);

        TitledBorder border = BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(ACCENT_BG, 1),
                title,
                TitledBorder.LEFT,
                TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 14),
                ACCENT_BG
        );
        border.setTitleColor(new Color(60, 60, 60));
        scrollPane.setBorder(border);

        return scrollPane;
    }

    private JPanel createStatisticsPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("СТАТИСТИКА РЕСТОРАНА");
        title.setFont(new Font("Segoe UI", Font.BOLD, 20));
        title.setForeground(new Color(60, 60, 60));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 20, 0));

        totalOrdersLabel = createStatLabel("0");
        waitingOrdersLabel = createStatLabel("0");
        cookingOrdersLabel = createStatLabel("0");
        readyOrdersLabel = createStatLabel("0");
        deliveredOrdersLabel = createStatLabel("0");

        JLabel queueInfoLabel = createStatLabel("0/20");
        queueInfoLabel.setName("queueInfoLabel");

        createStatLabel("0 сек");

        JPanel statsGrid = new JPanel(new GridLayout(3, 3, 15, 15));
        statsGrid.setBackground(LIGHT_BG);

        statsGrid.add(createStatCard("Всего заказов", totalOrdersLabel, ACCENT_BG));
        statsGrid.add(createStatCard("В ожидании", waitingOrdersLabel, ACCENT_BG));
        statsGrid.add(createStatCard("Готовятся", cookingOrdersLabel, ACCENT_BG));
        statsGrid.add(createStatCard("Готовы", readyOrdersLabel, ACCENT_BG));
        statsGrid.add(createStatCard("Доставлены", deliveredOrdersLabel, ACCENT_BG));
        statsGrid.add(createStatCard("Очередь кухни", queueInfoLabel, ACCENT_BG));

        JPanel queuePanel = new JPanel(new BorderLayout());
        queuePanel.setBackground(LIGHT_BG);
        queuePanel.setBorder(BorderFactory.createEmptyBorder(20, 0, 0, 0));

        queueProgressBar = new JProgressBar();
        queueProgressBar.setStringPainted(true);
        queueProgressBar.setFont(new Font("Segoe UI", Font.BOLD, 12));
        queueProgressBar.setBorder(BorderFactory.createEmptyBorder(5, 0, 5, 0));
        queueProgressBar.setForeground(BUTTON_ADD);
        queueProgressBar.setBackground(LIGHT_BG);

        JLabel queueTitle = new JLabel("Загрузка кухни (очередь 20 мест):");
        queueTitle.setFont(new Font("Segoe UI", Font.BOLD, 14));
        queueTitle.setForeground(new Color(60, 60, 60));

        queuePanel.add(queueTitle, BorderLayout.NORTH);
        queuePanel.add(queueProgressBar, BorderLayout.CENTER);

        JPanel summaryPanel = new JPanel(new BorderLayout());
        summaryPanel.setBackground(LIGHT_BG);
        summaryPanel.add(statsGrid, BorderLayout.NORTH);
        summaryPanel.add(queuePanel, BorderLayout.CENTER);

        panel.add(title, BorderLayout.NORTH);
        panel.add(summaryPanel, BorderLayout.CENTER);

        return panel;
    }

    private JLabel createStatLabel(String text) {
        JLabel label = new JLabel(text);
        label.setFont(new Font("Segoe UI", Font.BOLD, 24));
        label.setHorizontalAlignment(SwingConstants.CENTER);
        label.setForeground(new Color(60, 60, 60));
        return label;
    }

    private JPanel createStatCard(String title, JLabel valueLabel, Color color) {
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(LIGHT_BG);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(color, 1),
                BorderFactory.createEmptyBorder(15, 15, 15, 15)
        ));

        JLabel titleLabel = new JLabel(title);
        titleLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        titleLabel.setForeground(new Color(60, 60, 60));

        valueLabel.setForeground(new Color(60, 60, 60));

        card.add(titleLabel, BorderLayout.NORTH);
        card.add(valueLabel, BorderLayout.CENTER);

        return card;
    }

    private JPanel createLogPanel() {
        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(LIGHT_BG);
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel title = new JLabel("Журнал событий в реальном времени");
        title.setFont(new Font("Segoe UI", Font.BOLD, 18));
        title.setForeground(new Color(60, 60, 60));
        title.setBorder(BorderFactory.createEmptyBorder(0, 0, 15, 0));

        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Consolas", Font.PLAIN, 12));
        logArea.setBackground(LIGHT_BG);
        logArea.setForeground(new Color(60, 60, 60));
        logArea.setLineWrap(true);
        logArea.setWrapStyleWord(true);

        JScrollPane scrollPane = new JScrollPane(logArea);
        scrollPane.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));

        JPanel logControlPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        logControlPanel.setBackground(LIGHT_BG);

        JButton saveButton = createStyledButton("СОХРАНИТЬ ЛОГИ", ACCENT_BG);
        saveButton.addActionListener(e -> saveLogsToFile());

        logControlPanel.add(saveButton);

        panel.add(title, BorderLayout.NORTH);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(logControlPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void saveLogsToFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setDialogTitle("Сохранить логи смены");
        fileChooser.setSelectedFile(new java.io.File("restaurant_log_" +
                System.currentTimeMillis() + ".txt"));

        if (fileChooser.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
            try (java.io.PrintWriter writer = new java.io.PrintWriter(fileChooser.getSelectedFile())) {
                writer.write(logArea.getText());
                JOptionPane.showMessageDialog(this, "Логи успешно сохранены!",
                        "Сохранение", JOptionPane.INFORMATION_MESSAGE);
            } catch (Exception ex) {
                JOptionPane.showMessageDialog(this, "Ошибка при сохранении: " + ex.getMessage(),
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private JPanel createFooterPanel() {
        JPanel footer = new JPanel(new BorderLayout());
        footer.setBackground(ACCENT_BG);
        footer.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 0, 0, 0, ACCENT_BG.darker()),
                BorderFactory.createEmptyBorder(10, 20, 10, 20)
        ));

        JLabel infoLabel = new JLabel("Ресторан | Официантов: 3 | Поваров: 2 | Очередь кухни: 20 мест | Лимит заказов: 100");
        infoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        infoLabel.setForeground(TEXT_COLOR);

        timeLabel = new JLabel();
        timeLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        timeLabel.setForeground(TEXT_COLOR);

        footer.add(infoLabel, BorderLayout.WEST);
        footer.add(timeLabel, BorderLayout.EAST);

        return footer;
    }

    private void showAddOrderDialog() {
        JDialog dialog = new JDialog(this, "Добавить новый заказ", true);
        dialog.setSize(500, 450);
        dialog.setLayout(new BorderLayout());
        dialog.getContentPane().setBackground(MAIN_BG);
        dialog.setLocationRelativeTo(this);

        JPanel formPanel = new JPanel(new GridBagLayout());
        formPanel.setBackground(LIGHT_BG);
        formPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(ACCENT_BG, 1),
                BorderFactory.createEmptyBorder(20, 20, 20, 20)
        ));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.insets = new Insets(5, 5, 10, 5);
        gbc.weightx = 1.0;

        gbc.gridx = 0; gbc.gridy = 0;
        JLabel clientLabel = new JLabel("Имя клиента:");
        clientLabel.setForeground(new Color(60, 60, 60));
        formPanel.add(clientLabel, gbc);
        gbc.gridx = 1;
        JTextField clientField = new JTextField("Гость");
        clientField.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        clientField.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));
        formPanel.add(clientField, gbc);

        gbc.gridx = 0; gbc.gridy = 1;
        JLabel categoryLabel = new JLabel("Категория блюда:");
        categoryLabel.setForeground(new Color(60, 60, 60));
        formPanel.add(categoryLabel, gbc);
        gbc.gridx = 1;
        JComboBox<Order.DishCategory> categoryCombo = new JComboBox<>(Order.DishCategory.values());
        categoryCombo.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        categoryCombo.setBackground(LIGHT_BG);
        categoryCombo.setForeground(new Color(60, 60, 60));
        categoryCombo.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));
        formPanel.add(categoryCombo, gbc);

        gbc.gridx = 0; gbc.gridy = 2;
        JLabel dishLabel = new JLabel("Выберите блюдо:");
        dishLabel.setForeground(new Color(60, 60, 60));
        formPanel.add(dishLabel, gbc);
        gbc.gridx = 1;
        dishComboInDialog = new JComboBox<>();
        dishComboInDialog.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        dishComboInDialog.setBackground(LIGHT_BG);
        dishComboInDialog.setForeground(new Color(60, 60, 60));
        dishComboInDialog.setBorder(BorderFactory.createLineBorder(ACCENT_BG, 1));

        Order.DishCategory initialCategory = (Order.DishCategory) categoryCombo.getSelectedItem();
        if (initialCategory != null) {
            updateDishComboBox(initialCategory);
        }

        formPanel.add(dishComboInDialog, gbc);

        categoryCombo.addActionListener(e -> {
            Order.DishCategory selectedCategory = (Order.DishCategory) categoryCombo.getSelectedItem();
            if (selectedCategory != null) {
                updateDishComboBox(selectedCategory);
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 0));
        buttonPanel.setBackground(LIGHT_BG);

        JButton addButton = createStyledButton("ДОБАВИТЬ ЗАКАЗ", BUTTON_ADD);
        addButton.setPreferredSize(new Dimension(180, 40));

        JButton cancelButton = createStyledButton("ОТМЕНА", ACCENT_BG);
        cancelButton.setPreferredSize(new Dimension(120, 40));

        buttonPanel.add(addButton);
        buttonPanel.add(cancelButton);

        addButton.addActionListener(e -> {
            String clientName = clientField.getText().trim();
            if (clientName.isEmpty()) clientName = "Гость";

            String dishName = (String) dishComboInDialog.getSelectedItem();
            Order.DishCategory category = (Order.DishCategory) categoryCombo.getSelectedItem();

            if (dishName == null || dishName.isEmpty()) {
                JOptionPane.showMessageDialog(dialog, "Пожалуйста, выберите блюдо!",
                        "Ошибка", JOptionPane.ERROR_MESSAGE);
                return;
            }

            Order order = new Order(clientName, dishName, category);
            restaurant.addManualOrder(order);

            dialog.dispose();
        });

        cancelButton.addActionListener(e -> dialog.dispose());

        dialog.add(formPanel, BorderLayout.CENTER);

        gbc.gridx = 0; gbc.gridy = 3;
        gbc.gridwidth = 2;
        gbc.anchor = GridBagConstraints.CENTER;
        formPanel.add(buttonPanel, gbc);

        dialog.setLocationRelativeTo(this);
        dialog.setVisible(true);
    }

    private void updateDishComboBox(Order.DishCategory category) {
        if (category == null) return;

        SwingUtilities.invokeLater(() -> {
            if (dishComboInDialog != null) {
                dishComboInDialog.removeAllItems();

                String[] dishNames = categoryDishesMap.get(category);
                if (dishNames != null) {
                    for (String dishName : dishNames) {
                        dishComboInDialog.addItem(dishName);
                    }
                }

                if (dishComboInDialog.getItemCount() > 0) {
                    dishComboInDialog.setSelectedIndex(0);
                }
            }
        });
    }

    public void logMessage(String message) {
        SwingUtilities.invokeLater(() -> {
            String timestamp = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            logArea.append("[" + timestamp + "] " + message + "\n");

            int maxLines = 500;
            String[] lines = logArea.getText().split("\n");
            if (lines.length > maxLines) {
                StringBuilder trimmed = new StringBuilder();
                for (int i = lines.length - maxLines; i < lines.length; i++) {
                    trimmed.append(lines[i]).append("\n");
                }
                logArea.setText(trimmed.toString());
            }

            logArea.setCaretPosition(logArea.getDocument().getLength());
        });
    }

    public void updateOrderStatus(Order order) {
        SwingUtilities.invokeLater(() -> {
            activeOrders.put(order.getId(), order);
            updateOrdersTable();
        });
    }

    public void clearActiveOrders() {
        SwingUtilities.invokeLater(() -> {
            activeOrders.clear();
            updateOrdersTable();
        });
    }

    public void removeOldOrders() {
        SwingUtilities.invokeLater(() -> {
            long now = System.currentTimeMillis();
            Iterator<Map.Entry<String, Order>> it = activeOrders.entrySet().iterator();
            int removed = 0;
            while (it.hasNext()) {
                Map.Entry<String, Order> entry = it.next();
                Order order = entry.getValue();
                if (order.getStatus() == Order.OrderStatus.DELIVERED &&
                        (now - order.getCreationTime()) > 30000) {
                    it.remove();
                    removed++;
                }
            }
            if (removed > 0) {
                updateOrdersTable();
            }
        });
    }

    public void updateCookingProgress() {
        SwingUtilities.invokeLater(() -> {
            updateOrdersTable();
        });
    }

    public void updateQueueStatus(int currentSize, int maxSize) {
        SwingUtilities.invokeLater(() -> {
            if (queueProgressBar == null) {
                return;
            }

            int progress = maxSize > 0 ? (int) ((currentSize / (double) maxSize) * 100) : 0;
            queueProgressBar.setValue(progress);
            queueProgressBar.setString(currentSize + " / " + maxSize);

            if (progress > 80) {
                queueProgressBar.setForeground(BUTTON_STOP);
                queueProgressBar.setBackground(LIGHT_BG);
                queueProgressBar.setString("ПЕРЕПОЛНЕНА! " + currentSize + "/" + maxSize);
            } else if (progress > 50) {
                queueProgressBar.setForeground(BUTTON_PAUSE);
                queueProgressBar.setBackground(LIGHT_BG);
                queueProgressBar.setString("Высокая " + currentSize + "/" + maxSize);
            } else {
                queueProgressBar.setForeground(BUTTON_ADD);
                queueProgressBar.setBackground(LIGHT_BG);
                queueProgressBar.setString(currentSize + " / " + maxSize);
            }

            updateQueueInfoLabel(currentSize, maxSize);
        });
    }

    private void updateQueueInfoLabel(int currentSize, int maxSize) {
        SwingUtilities.invokeLater(() -> {
            Component[] components = getContentPane().getComponents();
            for (Component comp : components) {
                if (comp instanceof JPanel) {
                    JPanel mainPanel = (JPanel) comp;
                    Component[] mainComponents = mainPanel.getComponents();
                    for (Component mainComp : mainComponents) {
                        if (mainComp instanceof JTabbedPane) {
                            JTabbedPane tabbedPane = (JTabbedPane) mainComp;
                            Component statsTab = tabbedPane.getComponentAt(2);
                            if (statsTab instanceof JPanel) {
                                JPanel statsPanel = (JPanel) statsTab;
                                findAndUpdateQueueLabel(statsPanel, currentSize, maxSize);
                                return;
                            }
                        }
                    }
                }
            }
        });
    }

    private void findAndUpdateQueueLabel(Container container, int currentSize, int maxSize) {
        for (Component comp : container.getComponents()) {
            if (comp instanceof JLabel && "queueInfoLabel".equals(comp.getName())) {
                JLabel label = (JLabel) comp;
                label.setText(currentSize + "/" + maxSize);
                label.setForeground(new Color(60, 60, 60));
                return;
            }

            if (comp instanceof Container) {
                findAndUpdateQueueLabel((Container) comp, currentSize, maxSize);
            }
        }
    }

    public void updateStatistics() {
        if (updateScheduled.get()) {
            return;
        }

        updateScheduled.set(true);

        SwingUtilities.invokeLater(() -> {
            try {
                if (totalOrdersLabel != null) {
                    totalOrdersLabel.setText(String.valueOf(restaurant.getTotalOrders()));
                    waitingOrdersLabel.setText(String.valueOf(restaurant.getWaitingOrders()));
                    cookingOrdersLabel.setText(String.valueOf(restaurant.getCookingOrders()));
                    readyOrdersLabel.setText(String.valueOf(restaurant.getReadyOrdersCount()));
                    deliveredOrdersLabel.setText(String.valueOf(restaurant.getDeliveredOrders()));

                    updateWaitersTable();
                    updateCooksTable();

                    int currentSize = restaurant.getQueueSize();
                    int maxSize = restaurant.getMaxQueueSize();
                    updateQueueStatus(currentSize, maxSize);

                    boolean isRunning = restaurant.isRunning();
                    boolean isPaused = restaurant.isPaused();

                    startButton.setEnabled(!isRunning);
                    pauseButton.setEnabled(isRunning);
                    stopButton.setEnabled(isRunning);

                    if (isPaused) {
                        pauseButton.setText("ПРОДОЛЖИТЬ");
                        pauseButton.setBackground(BUTTON_PAUSE.brighter());
                    } else {
                        pauseButton.setText("ПАУЗА");
                        pauseButton.setBackground(BUTTON_PAUSE);
                    }
                }
            } finally {
                updateScheduled.set(false);
            }
        });
    }

    public void updateWaiterStatus() {
        updateStatistics();
    }

    public void updateCookStatus() {
        updateStatistics();
    }

    private void updateOrdersTable() {
        SwingUtilities.invokeLater(() -> {
            ordersTableModel.setRowCount(0);

            int orderNumber = 1;
            for (Order order : activeOrders.values()) {
                Object[] row = {
                        orderNumber++,
                        order.getClientName(),
                        order.getDishName(),
                        order.getDishCategory().getDisplayName(),
                        order.getStatus().getDisplayName(),
                        order.getAssignedWaiter() != null ? order.getAssignedWaiter() : "-",
                        formatTime(order.getTotalTime()),
                        createProgressBar(order)
                };
                ordersTableModel.addRow(row);
            }
        });
    }

    private String formatTime(long milliseconds) {
        long seconds = milliseconds / 1000;
        if (seconds < 60) {
            return seconds + " сек";
        } else {
            long minutes = seconds / 60;
            long remainingSeconds = seconds % 60;
            return minutes + ":" + String.format("%02d", remainingSeconds);
        }
    }

    private JProgressBar createProgressBar(Order order) {
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setStringPainted(true);
        progressBar.setFont(new Font("Segoe UI", Font.BOLD, 10));

        switch (order.getStatus()) {
            case WAITING_FOR_COOKING:
                progressBar.setValue(20);
                progressBar.setString("Ожидание");
                progressBar.setForeground(STATUS_WAITING.darker());
                progressBar.setBackground(STATUS_WAITING);
                break;

            case COOKING:
                long elapsed = System.currentTimeMillis() - order.getStartCookingTime();
                int totalTime = order.getDishCategory().getPreparationTime();
                int progress = 20 + (int) ((elapsed / (double) totalTime) * 60);
                progress = Math.min(80, Math.max(20, progress));
                progressBar.setValue(progress);
                progressBar.setString("Готовится " + progress + "%");
                progressBar.setForeground(STATUS_COOKING.darker());
                progressBar.setBackground(STATUS_COOKING);
                break;

            case READY:
                progressBar.setValue(90);
                progressBar.setString("Готово");
                progressBar.setForeground(STATUS_READY.darker());
                progressBar.setBackground(STATUS_READY);
                break;

            case DELIVERED:
                progressBar.setValue(100);
                progressBar.setString("Доставлен");
                progressBar.setForeground(STATUS_DELIVERED.darker());
                progressBar.setBackground(STATUS_DELIVERED);
                break;

            default:
                progressBar.setValue(10);
                progressBar.setString("Создан");
                progressBar.setForeground(STATUS_CREATED.darker());
                progressBar.setBackground(STATUS_CREATED);
        }

        return progressBar;
    }

    private void updateWaitersTable() {
        waitersTableModel.setRowCount(0);

        for (Waiter waiter : restaurant.getWaiters()) {
            String productivity = "";
            int served = waiter.getOrdersServed();
            if (served > 0) {
                int stars = Math.min(5, served / 2 + 1);
                productivity = "★".repeat(stars);
            } else {
                productivity = "-";
            }

            JProgressBar loadBar = new JProgressBar(0, 100);
            loadBar.setValue(waiter.getLoadPercentage());
            loadBar.setString(waiter.getCurrentOrders() + "/" + waiter.getMaxConcurrentOrders());
            loadBar.setStringPainted(true);
            loadBar.setForeground(BUTTON_ADD);
            loadBar.setBackground(LIGHT_BG);

            Object[] row = {
                    waiter.getName(),
                    waiter.isWorking() ? "Работает" : "Отдыхает",
                    waiter.getOrdersServed() + "/" + waiter.getOrdersAccepted(),
                    productivity,
                    loadBar
            };
            waitersTableModel.addRow(row);
        }
    }

    private void updateCooksTable() {
        cooksTableModel.setRowCount(0);

        for (Cook cook : restaurant.getCooks()) {
            String productivity = "";
            int cooked = cook.getOrdersCooked();
            if (cooked > 0) {
                int stars = Math.min(5, cooked / 2 + 1);
                productivity = "★".repeat(stars);
            } else {
                productivity = "-";
            }

            Object[] row = {
                    cook.getName(),
                    cook.isCooking() ? "Готовит" : "Отдыхает",
                    cook.getOrdersCooked(),
                    productivity
            };
            cooksTableModel.addRow(row);
        }
    }

    private void startClock() {
        Timer timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                SwingUtilities.invokeLater(() -> {
                    if (timeLabel != null) {
                        String time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
                        timeLabel.setText("Время " + time);
                    }
                });
            }
        }, 0, 1000);
    }
}