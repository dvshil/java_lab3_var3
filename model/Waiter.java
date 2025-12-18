package model;

import gui.RestaurantGUI;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Waiter implements Runnable {
    private final String name;
    private final Restaurant restaurant;
    private final RestaurantGUI gui;
    private volatile boolean isWorking = false;
    private volatile boolean isPaused = false;
    private int ordersServed = 0;
    private int ordersAccepted = 0;

    private static final int MAX_CONCURRENT_ORDERS = 3;
    private final AtomicInteger currentOrders = new AtomicInteger(0);

    private static final int MAX_TOTAL_ORDERS_PER_WAITER = 50;
    private int totalOrdersHandled = 0;

    //обработка заказа
    private final BlockingQueue<Order> orderAcceptanceQueue = new ArrayBlockingQueue<>(10);
    private Thread orderAcceptanceThread;

    private static final int ORDER_ACCEPTANCE_TIME = 800;

    public Waiter(String name, Restaurant restaurant, RestaurantGUI gui) {
        this.name = name;
        this.restaurant = restaurant;
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.logMessage("[ОФИЦИАНТ]" + name + " начал смену (макс. заказов: " + MAX_CONCURRENT_ORDERS + ")");

        //поток для принятия заказов
        orderAcceptanceThread = new Thread(() -> {
            while (isWorking || !orderAcceptanceQueue.isEmpty()) {
                try {
                    if (Thread.currentThread().isInterrupted()) {
                        break;
                    }

                    restaurant.checkPause();

                    Order order = orderAcceptanceQueue.poll(100, TimeUnit.MILLISECONDS);

                    if (order != null) {
                        processOrder(order);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            gui.logMessage("[ОФИЦИАНТ]" + name + ": поток приема заказов завершен");
        });
        orderAcceptanceThread.start();

        while (isWorking) {
            try {
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }

                restaurant.checkPause();

                Order readyOrder = restaurant.takeReadyOrder(name);
                if (readyOrder != null) {
                    if (restaurant.markOrderAsTaken(readyOrder.getId())) {
                        deliverOrder(readyOrder);
                    }
                }

                if (currentOrders.get() >= MAX_CONCURRENT_ORDERS) {
                    Thread.sleep(1000);
                    continue;
                }

                Thread.sleep(300);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }

        if (orderAcceptanceThread != null && orderAcceptanceThread.isAlive()) {
            orderAcceptanceThread.interrupt();
            try {
                orderAcceptanceThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        gui.logMessage(name + " закончил смену. Принято: " + ordersAccepted + ", Обслужено: " + ordersServed);
    }

    public void acceptOrder(Order order) {
        try {
            if (orderAcceptanceQueue.size() < 10) {
                orderAcceptanceQueue.put(order);
                gui.logMessage("[ОФИЦИАНТ]" + name + " принял заказ в очередь: " + order);
            } else {
                gui.logMessage("[ОФИЦИАНТ]" + name + " слишком занят! Очередь приема переполнена");
                gui.logMessage("Заказ " + order + " будет ждать в общей очереди");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            gui.logMessage("Прием заказа прерван: " + order);
        }
    }

    //обработка заказа
    private void processOrder(Order order) {
        try {
            restaurant.checkPause();

            if (totalOrdersHandled >= MAX_TOTAL_ORDERS_PER_WAITER) {
                gui.logMessage("[ОФИЦИАНТ]" + name + " достиг лимита заказов (" + MAX_TOTAL_ORDERS_PER_WAITER + ")");
                return;
            }

            if (currentOrders.get() >= MAX_CONCURRENT_ORDERS) {
                gui.logMessage("[ОФИЦИАНТ]" + name + " слишком занят! Клиент " + order.getClientName() + " ждет...");

                int waitTime = 0;
                while (currentOrders.get() >= MAX_CONCURRENT_ORDERS && isWorking && waitTime < 3000) {
                    restaurant.checkPause();
                    Thread.sleep(500);
                    waitTime += 500;
                }

                if (!isWorking || currentOrders.get() >= MAX_CONCURRENT_ORDERS) {
                    gui.logMessage("[ОФИЦИАНТ] " + name + " все еще занят, заказ возвращен в общую очередь: " + order);
                    return;
                }
            }

            Thread.sleep(ORDER_ACCEPTANCE_TIME);

            currentOrders.incrementAndGet();
            ordersAccepted++;
            totalOrdersHandled++;

            gui.logMessage("[ОФИЦИАНТ]" + name + " обработал заказ: " + order +
                    " (активных: " + currentOrders.get() + "/" + MAX_CONCURRENT_ORDERS);

            order.setStatus(Order.OrderStatus.WAITING_FOR_COOKING);
            order.setAssignedWaiter(name);
            gui.updateOrderStatus(order);

            boolean added = restaurant.addToKitchenQueue(order, name);
            if (!added) {
                currentOrders.decrementAndGet();
                gui.logMessage("Не удалось добавить заказ " + order + " в очередь кухни, заказ ждет...");
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            currentOrders.decrementAndGet();
            gui.logMessage("Обработка заказа прервана: " + order);
        }
    }

    private void deliverOrder(Order order) throws InterruptedException {
        order.setStatus(Order.OrderStatus.DELIVERED);

        int deliveryTime = 500 + order.getDishCategory().getPreparationTime() / 10;
        gui.logMessage("[ОФИЦИАНТ]" + name + " несет заказ: " + order);

        int steps = 5;
        int stepTime = deliveryTime / steps;

        for (int i = 0; i < steps; i++) {
            restaurant.checkPause();
            Thread.sleep(stepTime);
        }

        restaurant.deliverOrder(order);
        currentOrders.decrementAndGet();
        ordersServed++;
        gui.updateWaiterStatus();

        long totalTime = order.getTotalTime() / 1000;
        long waitingTime = order.getWaitingTime() / 1000;
        long cookingTime = order.getCookingTime() / 1000;

        gui.logMessage(String.format("[ОФИЦИАНТ]" + name + " доставил заказ за %dсек (ожидание: %dсек, готовка: %dсек)",
                totalTime, waitingTime, cookingTime));
    }

    public void setWorking(boolean working) {
        if (working && !isWorking) {
            gui.logMessage("[ОФИЦИАНТ]" + name + " начал смену");
        } else if (!working && isWorking) {
            gui.logMessage("[ОФИЦИАНТ]" + name + " закончил смену. Принято: " + ordersAccepted + ", Обслужено: " + ordersServed);
        }
        this.isWorking = working;

        if (!working && orderAcceptanceThread != null) {
            orderAcceptanceThread.interrupt();
        }
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            gui.logMessage(name + " приостановил работу");
        } else {
            gui.logMessage(name + " возобновил работу");
        }
    }

    public String getName() { return name; }
    public int getOrdersServed() { return ordersServed; }
    public int getOrdersAccepted() { return ordersAccepted; }
    public int getCurrentOrders() { return currentOrders.get(); }
    public int getMaxConcurrentOrders() { return MAX_CONCURRENT_ORDERS; }
    public boolean isWorking() { return isWorking; }

    public int getLoadPercentage() {
        return (currentOrders.get() * 100) / MAX_CONCURRENT_ORDERS;
    }
}