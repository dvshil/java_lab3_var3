package model;

import java.util.UUID;

public class Order {
    private final String id;
    private final String clientName;
    private final String dishName;
    private final DishCategory dishCategory;
    private OrderStatus status;
    private final long creationTime;
    private long startCookingTime;
    private long finishCookingTime;
    private String assignedWaiter;

    public enum DishCategory {
        APPETIZER("Закуска", 1000, new String[]{
                "Маслины", "Оливки", "Сырная тарелка", "Орехи",
                "Хлебная корзина", "Овощная нарезка", "Брускетта"
        }),

        SALAD("Салат", 1200, new String[]{
                "Салат Цезарь", "Салат Греческий", "Салат Оливье",
                "Салат Крабовый", "Сельдь под шубой"
        }),

        SOUP("Суп", 2000, new String[]{
                "Борщ", "Щи", "Солянка", "Грибной крем-суп",
                "Куриный бульон", "Том Ям", "Харчо"
        }),

        MAIN_COURSE("Основное блюдо", 3500, new String[]{
                "Стейк Рибай", "Котлеты по-киевски", "Утка с яблоками",
                "Рыба на гриле", "Курица гриль", "Свиные ребрышки",
                "Бефстроганов", "Жаркое", "Шашлык"
        }),

        SIDE_DISH("Гарнир", 1500, new String[]{
                "Картофельное пюре", "Жареная картошка",
                "Гречка", "Рис басмати", "Овощи на гриле",
                "Картофель фри", "Тушеная капуста"
        }),

        DESSERT("Десерт", 1800, new String[]{
                "Тирамису", "Чизкейк", "Медовик",
                "Мороженое", "Шоколадный фондан",
                "Панна котта", "Эклеры"
        }),

        DRINK("Напиток", 300, new String[]{
                "Кофе латте", "Чай черный", "Апельсиновый сок",
                "Лимонад мятный", "Морс клюквенный",
                "Минеральная вода", "Капучино"
        });

        private final String displayName;
        private final int preparationTime;
        private final String[] dishNames;

        DishCategory(String displayName, int preparationTime, String[] dishNames) {
            this.displayName = displayName;
            this.preparationTime = preparationTime;
            this.dishNames = dishNames;
        }

        public String getDisplayName() { return displayName; }
        public int getPreparationTime() { return preparationTime; }

        public String getRandomDishName() {
            return dishNames[(int) (Math.random() * dishNames.length)];
        }

        public String[] getDishNamesForDisplay() {
            return dishNames;
        }
    }

    public enum OrderStatus {
        CREATED("Создан"),
        WAITING_FOR_COOKING("В ожидании"),
        COOKING("Готовится"),
        READY("Готов"),
        DELIVERED("Доставлен");

        private final String displayName;

        OrderStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() { return displayName; }
    }

    public Order(String clientName, String dishName, DishCategory dishCategory) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.clientName = clientName;
        this.dishName = dishName;
        this.dishCategory = dishCategory;
        this.status = OrderStatus.CREATED;
        this.creationTime = System.currentTimeMillis();
    }

    public static Order createRandomOrder(String clientName) {
        DishCategory category = DishCategory.values()[
                (int) (Math.random() * DishCategory.values().length)];
        String dishName = category.getRandomDishName();
        return new Order(clientName, dishName, category);
    }

    public String getId() { return id; }
    public String getClientName() { return clientName; }
    public String getDishName() { return dishName; }
    public DishCategory getDishCategory() { return dishCategory; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public long getCreationTime() { return creationTime; }
    public long getStartCookingTime() { return startCookingTime; }
    public void setStartCookingTime(long startCookingTime) {
        this.startCookingTime = startCookingTime;
    }

    public void setFinishCookingTime(long finishCookingTime) {
        this.finishCookingTime = finishCookingTime;
    }
    public String getAssignedWaiter() { return assignedWaiter; }
    public void setAssignedWaiter(String waiter) { this.assignedWaiter = waiter; }

    public long getWaitingTime() {
        if (startCookingTime == 0) return System.currentTimeMillis() - creationTime;
        return startCookingTime - creationTime;
    }

    public long getCookingTime() {
        if (finishCookingTime == 0 || startCookingTime == 0) return 0;
        return finishCookingTime - startCookingTime;
    }

    public long getTotalTime() {
        if (status == OrderStatus.DELIVERED) return finishCookingTime - creationTime;
        return System.currentTimeMillis() - creationTime;
    }

    @Override
    public String toString() {
        return dishName + " для " + clientName;
    }
}