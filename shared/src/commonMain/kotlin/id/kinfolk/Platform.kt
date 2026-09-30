package id.kinfolk

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform