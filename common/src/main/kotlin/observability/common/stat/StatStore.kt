package observability.common.stat

interface StatStore {
    fun append(stat: Stat<*>)

    fun lastValue(statType: StatType): Stat<*>?
}
