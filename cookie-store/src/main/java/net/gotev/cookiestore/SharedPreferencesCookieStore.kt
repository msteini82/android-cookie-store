package net.gotev.cookiestore

import android.content.Context
import android.util.Log
import com.google.gson.*
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type
import java.net.HttpCookie
import java.net.URI

class HttpCookieArrayAdapter : JsonSerializer<ArrayList<HttpCookie>>, JsonDeserializer<ArrayList<HttpCookie>> {

    override fun serialize(
        src: ArrayList<HttpCookie>?,
        typeOfSrc: Type?,
        context: JsonSerializationContext?
    ): JsonElement {

        val whenCreatedField = HttpCookie::class.java.getDeclaredField("whenCreated")
        whenCreatedField.isAccessible = true

        val array = JsonArray()

        if (src != null) {
            for (cookie in src) {

                val obj = JsonObject()

                obj.addProperty("name", cookie.name)
                obj.addProperty("value", cookie.value)
                obj.addProperty("domain", cookie.domain)
                obj.addProperty("path", cookie.path)
                obj.addProperty("httpOnly", cookie.isHttpOnly)
                obj.addProperty("secure", cookie.secure)
                obj.addProperty("toDiscard", cookie.discard)
                obj.addProperty("maxAge", cookie.maxAge)
                obj.addProperty("whenCreated", whenCreatedField.get(cookie) as Long)
                obj.addProperty("version", cookie.version)

                array.add(obj)

            }
        }

        return array

    }

    override fun deserialize(
        json: JsonElement?,
        typeOfT: Type?,
        context: JsonDeserializationContext?
    ): ArrayList<HttpCookie> {

        val whenCreatedField = HttpCookie::class.java.getDeclaredField("whenCreated")
        whenCreatedField.isAccessible = true

        val list = arrayListOf<HttpCookie>()

        if (json != null) {
            for (el in json as JsonArray) {

                val obj = el.asJsonObject

                val cookie = HttpCookie(obj["name"].asString, obj["value"].asString)

                cookie.domain = obj["domain"].asString
                cookie.path = obj["path"].asString
                cookie.isHttpOnly = obj["httpOnly"].asBoolean
                cookie.secure = obj["secure"].asBoolean
                cookie.discard = obj["toDiscard"].asBoolean
                cookie.maxAge = obj["maxAge"].asLong
                cookie.version = obj["version"].asInt

                whenCreatedField.set(cookie, obj["whenCreated"].asLong)

                list.add(cookie)

            }
        }

        return list

    }

}

open class SharedPreferencesCookieStore(
    context: Context,
    private val name: String
) : InMemoryCookieStore(name) {

    private val preferences = context.getSharedPreferences(name, Context.MODE_PRIVATE)
    
    private val listType = object : TypeToken<ArrayList<HttpCookie>>() {}.type
    private val gson = GsonBuilder().registerTypeAdapter(listType, HttpCookieArrayAdapter()).create()

    init {
        synchronized(SharedPreferencesCookieStore::class.java) {
            preferences.all.forEach { (key, value) ->
                try {
                    val index = URI.create(key)
                    val cookies: ArrayList<HttpCookie> = gson.fromJson(value.toString(), listType)

                    uriIndex[index] = cookies
                } catch (exception: Throwable) {
                    Log.e(
                        javaClass.simpleName,
                        "Error while loading key = $key, value = $value from cookie store named $name",
                        exception
                    )
                }
            }
        }
    }

    override fun removeAll(): Boolean = synchronized(SharedPreferencesCookieStore::class.java) {
        super.removeAll()
        preferences.edit().clear().commit()
        true
    }

    override fun add(uri: URI?, cookie: HttpCookie?) {
        synchronized(SharedPreferencesCookieStore::class.java) {
            super.add(uri, cookie)

            uri?.let {
                val index = getEffectiveURI(uri)
                uriIndex[index]?.let { cookies ->
                    preferences
                        .edit()
                        .putString(index.toString(), gson.toJson(ArrayList(cookies), listType))
                        .commit()
                }
            }
        }
    }

    override fun remove(uri: URI?, cookie: HttpCookie?): Boolean =
        synchronized(SharedPreferencesCookieStore::class.java) {
            val result = super.remove(uri, cookie)

            uri?.let {
                val index = getEffectiveURI(uri)
                val cookies = uriIndex[index]

                preferences.edit().apply {
                    if (cookies == null) {
                        remove(index.toString())
                    } else {
                        putString(index.toString(), gson.toJson(ArrayList(cookies), listType))
                    }
                }.commit()
            }

            result
        }
}
