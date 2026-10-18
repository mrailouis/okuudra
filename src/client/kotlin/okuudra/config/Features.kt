package okuudra.config

object Features {
    private val registered = ArrayList<Feature>()

    val all: List<Feature> get() = registered

    fun register(vararg features: Feature) {
        registered.addAll(features)
    }
}
