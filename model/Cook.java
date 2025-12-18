package model;

import gui.RestaurantGUI;

public class Cook implements Runnable {
    private final String name;
    private final Restaurant restaurant;
    private final RestaurantGUI gui;
    private volatile boolean isCooking = false;
    private volatile boolean isPaused = false;
    private int ordersCooked = 0;

    public Cook(String name, Restaurant restaurant, RestaurantGUI gui) {
        this.name = name;
        this.restaurant = restaurant;
        this.gui = gui;
    }

    @Override
    public void run() {
        gui.logMessage("[ПОВАР]" + name + " готов к работе");

        while (isCooking) {
            try {
                restaurant.checkPause();

                Order order = restaurant.takeFromKitchenQueue();

                cookOrder(order);

                ordersCooked++;
                gui.updateCookStatus();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                gui.logMessage(name + " был прерван");
                break;
            } catch (Exception e) {
                gui.logMessage("Ошибка у повара " + name + ": " + e.getMessage());
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    //стадии приготовления
    private void cookOrder(Order order) throws InterruptedException {
        order.setStatus(Order.OrderStatus.COOKING);
        order.setStartCookingTime(System.currentTimeMillis());

        gui.logMessage("[ПОВАР]" + name + " начинает готовить: " +
                order.getDishName() +
                " (" + order.getDishCategory().getDisplayName() + ")");

        gui.updateOrderStatus(order);

        int cookingTime = order.getDishCategory().getPreparationTime();

        int steps = 10;
        int stepTime = cookingTime / steps;

        for (int i = 1; i <= steps; i++) {
            if (!isCooking) break;

            restaurant.checkPause();

            Thread.sleep(stepTime);

            gui.updateCookingProgress();
        }

        if (isCooking) {
            order.setFinishCookingTime(System.currentTimeMillis());
            restaurant.completeOrder(order);

            gui.logMessage("[ПОВАР]" + name + " приготовил: " +
                    order.getDishName() + " за " +
                    (order.getCookingTime() / 1000) + "сек");
        }
    }

    public void setCooking(boolean cooking) {
        if (cooking && !isCooking) {
            gui.logMessage("[ПОВАР]" + name + " готов к работе");
        } else if (!cooking && isCooking) {
            gui.logMessage("[ПОВАР]" + name + " закончил смену. Приготовлено: " + ordersCooked);
        }
        this.isCooking = cooking;
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        if (paused) {
            gui.logMessage(name + " приостановил готовку");
        } else {
            gui.logMessage(name + " возобновил готовку");
        }
    }

    public String getName() { return name; }
    public int getOrdersCooked() { return ordersCooked; }
    public boolean isCooking() { return isCooking; }
}