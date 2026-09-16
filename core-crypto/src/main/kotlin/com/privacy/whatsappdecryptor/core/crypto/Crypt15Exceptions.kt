package com.privacy.whatsappdecryptor.core.crypto

open class Crypt15Exception(message: String, cause: Throwable? = null) : Exception(message, cause)

class Crypt15ParseException(message: String, cause: Throwable? = null) : Crypt15Exception(message, cause)

class Crypt15AuthenticationException(message: String, cause: Throwable? = null) : Crypt15Exception(message, cause)

class Crypt15DecompressionException(message: String, cause: Throwable? = null) : Crypt15Exception(message, cause)

class Crypt15StorageException(message: String, cause: Throwable? = null) : Crypt15Exception(message, cause)

class Crypt15InvalidDatabaseException(message: String, cause: Throwable? = null) : Crypt15Exception(message, cause)
