package com.pylikv.queuewatch


/**
 * Тип транспортного средства.
 *
 * Соответствует отдельным очередям,
 * которые сервер API возвращает для КПП.
 */
enum class VehicleType {

    /** Легковой автомобиль */
    CAR,

    /** Грузовой автомобиль */
    TRUCK,

    /** Автобус */
    BUS,

    /** Мотоцикл */
    MOTORCYCLE
}


/**
 * Одна запись транспортного средства,
 * полученная непосредственно из ответа сервера.
 */
data class QueueVehicle(

    /**
     * Регистрационный номер.
     */
    val regnum: String,

    /**
     * Серверный статус.
     */
    val status: Int?,

    /**
     * Серверный order_id.
     *
     * Для живой очереди это текущая позиция.
     */
    val orderId: Int?,

    /**
     * Серверный тип очереди.
     */
    val typeQueue: Int?,

    /**
     * Дата регистрации.
     */
    val registrationDate: String?,

    /**
     * Дата изменения.
     */
    val changedDate: String?,

    /**
     * Тип транспорта.
     */
    val vehicleType: VehicleType = VehicleType.CAR
) {

    /**
     * Позиция из order_id.
     *
     * Никакого собственного расчёта здесь нет.
     */
    val position: Int?
        get() = orderId
}


/**
 * Состояние транспортного средства.
 */
enum class VehicleState {

    /**
     * Автомобиль находится
     * в живой очереди.
     */
    IN_QUEUE,

    /**
     * Сервер явно сообщил status == 3.
     *
     * Подтверждённый вызов.
     */
    CALLED,

    /**
     * Состояние невозможно
     * однозначно определить.
     */
    UNKNOWN
}


/**
 * Историческая точка наблюдения.
 */
data class QueueHistoryPoint(

    /**
     * Время получения снимка.
     */
    val timestampMillis: Long,

    /**
     * Позиция, полученная от сервера.
     */
    val position: Int?,

    /**
     * Состояние автомобиля.
     */
    val state: VehicleState
)


/**
 * Скорость движения очереди.
 */
data class QueueSpeed(

    /**
     * Количество позиций в час.
     */
    val positionsPerHour: Double,

    /**
     * Среднее количество минут
     * на одну позицию.
     */
    val minutesPerPosition: Double
)


/**
 * Прогноз для транспортного средства.
 */
data class QueueForecast(

    /**
     * Последняя подтверждённая
     * сервером позиция.
     */
    val currentPosition: Int?,

    /**
     * Количество позиций впереди.
     */
    val positionsAhead: Int?,

    /**
     * Скорость, использованная
     * для прогноза.
     */
    val speed: QueueSpeed?,

    /**
     * Расчётное время до вызова
     * в минутах.
     */
    val estimatedMinutes: Double?
)


/**
 * Одна статистическая ячейка.
 *
 * Комбинация:
 *
 * КПП
 * + тип транспорта
 * + день недели
 * + час.
 *
 * Фактически данные хранятся
 * отдельно для каждого такого интервала.
 */
data class QueueStatisticsCell(

    /**
     * День недели.
     *
     * Calendar.SUNDAY ... Calendar.SATURDAY
     */
    val dayOfWeek: Int,

    /**
     * Час суток.
     *
     * 0 ... 23
     */
    val hour: Int,

    /**
     * Количество подтверждённых
     * вызовов автомобилей.
     */
    val calledCount: Int = 0,

    /**
     * Количество наблюдений,
     * когда данный час реально
     * контролировался программой.
     */
    val observedCount: Int = 0,

    /**
     * Суммарное фактическое
     * время ожидания автомобилей
     * в минутах.
     */
    val totalWaitingMinutes: Double = 0.0,

    /**
     * Количество автомобилей,
     * для которых удалось определить
     * время от регистрации до вызова.
     *
     * ВАЖНО:
     * здесь обязательно val.
     */
    val waitingSamples: Int = 0

) {

    /**
     * Среднее количество вызванных
     * автомобилей за один наблюдаемый час.
     *
     * Например:
     *
     * calledCount = 20
     * observedCount = 1
     *
     * результат = 20 поз./ч.
     *
     * Если observedCount = 2,
     * а вызовов было 40,
     * результат также = 20 поз./ч.
     */
    val callsPerObservedHour: Double
        get() =
            if (observedCount > 0) {
                calledCount.toDouble() /
                    observedCount
            } else {
                0.0
            }


    /**
     * Среднее фактическое время
     * ожидания от регистрации
     * до обнаруженного status=3.
     */
    val averageWaitingMinutes: Double?
        get() =
            if (waitingSamples > 0) {
                totalWaitingMinutes /
                    waitingSamples
            } else {
                null
            }
}


/**
 * Результат определения скорости
 * для конкретного часового интервала.
 */
data class HourlySpeed(

    /**
     * День недели.
     */
    val dayOfWeek: Int,

    /**
     * Час суток.
     */
    val hour: Int,

    /**
     * Скорость в позициях/автомобилях
     * в час.
     */
    val positionsPerHour: Double,

    /**
     * true — именно этот день/час
     * уже имеет собственную статистику.
     *
     * false — использована более общая
     * накопленная статистика.
     */
    val observed: Boolean
)
