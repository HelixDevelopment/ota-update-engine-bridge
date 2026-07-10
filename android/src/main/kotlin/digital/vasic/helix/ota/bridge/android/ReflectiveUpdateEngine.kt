/*
 * Helix OTA — reflective access to the @SystemApi android.os.UpdateEngine.
 *
 * UpdateEngine / UpdateEngineCallback are @SystemApi and are NOT present in the public
 * android.jar, so a direct compile-time reference would not build against the public
 * SDK. We therefore bind to them at RUNTIME via reflection, which lets this module
 * compile against the public SDK while still calling the real engine on a device that
 * has it (platform-signed / system-UID app; spec §9).
 *
 * Methods used (verified surface, spec §2):
 *   void applyPayload(String url, long offset, long size, String[] headerKeyValuePairs)
 *   boolean bind(UpdateEngineCallback callback)
 *   void unbind()
 * Callback (abstract class android.os.UpdateEngineCallback):
 *   void onStatusUpdate(int status, float percent)
 *   void onPayloadApplicationComplete(int errorCode)
 */
package digital.vasic.helix.ota.bridge.android

import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method
import java.lang.reflect.Proxy

/**
 * Typed callback the bridge implements; [ReflectiveUpdateEngine] adapts the raw
 * reflective UpdateEngineCallback invocations onto it.
 */
interface RawEngineCallback {
    fun onStatusUpdate(status: Int, percent: Float)
    fun onPayloadApplicationComplete(errorCode: Int)
}

/**
 * Thin reflective wrapper over a real `android.os.UpdateEngine` instance.
 *
 * Constructing this loads the @SystemApi classes; on a device/image where they are
 * absent it throws (ClassNotFound/NoSuchMethod) — callers that want to stay
 * test-friendly inject a fake [UpdateEngineBridge] instead of using this directly.
 */
class ReflectiveUpdateEngine(
    classLoader: ClassLoader = ReflectiveUpdateEngine::class.java.classLoader!!,
) {
    private val engineClass: Class<*> = Class.forName("android.os.UpdateEngine", true, classLoader)
    private val callbackClass: Class<*> = Class.forName("android.os.UpdateEngineCallback", true, classLoader)

    private val engine: Any = engineClass.getDeclaredConstructor().newInstance()

    private val applyPayloadMethod: Method = engineClass.getMethod(
        "applyPayload",
        String::class.java,
        Long::class.javaPrimitiveType,
        Long::class.javaPrimitiveType,
        Array<String>::class.java,
    )
    private val bindMethod: Method = engineClass.getMethod("bind", callbackClass)
    private val unbindMethod: Method = engineClass.getMethod("unbind")

    /** Call the real `applyPayload(url, offset, size, props)` (spec §2). */
    fun applyPayload(url: String, offset: Long, size: Long, headerKeyValuePairs: Array<String>) {
        applyPayloadMethod.invoke(engine, url, offset, size, headerKeyValuePairs)
    }

    /**
     * Bind [callback] to the engine. The abstract `UpdateEngineCallback` cannot be
     * proxied by [Proxy] (it is a class, not an interface), so we generate a concrete
     * subclass dynamically is not possible without bytecode tooling — instead we use a
     * [Proxy] over the callback's *interface view* when available, else fall back to a
     * hand-rolled subclass loaded reflectively. AOSP's UpdateEngineCallback is an
     * abstract class with two abstract methods; we subclass it via a generated proxy
     * using ByteBuddy-free java.lang.reflect.Proxy is not viable, so we require the
     * platform path to provide a subclass. For the public-SDK compile we route through
     * [makeCallback], which on-device builds a subclass instance.
     *
     * Returns whether the platform actually accepted the binding. The real
     * `android.os.UpdateEngine.bind(UpdateEngineCallback)` returns `boolean` (see the
     * "verified surface" table above) — it returns `false` (without throwing) when the
     * binder registration fails, e.g. because `update_engine` could not be reached. A
     * caller that ignores a `false` result here would wait forever for status /
     * completion callbacks that will never arrive, since no callback was ever actually
     * registered.
     */
    fun bind(callback: Any): Boolean = bindMethod.invoke(engine, callback) as Boolean

    fun unbind() {
        unbindMethod.invoke(engine)
    }

    /**
     * Build a concrete `UpdateEngineCallback` instance that forwards to [delegate].
     *
     * `UpdateEngineCallback` is an abstract CLASS, so [Proxy] (interfaces only) cannot
     * implement it directly. We therefore load a small generated subclass that the
     * platform provides. When that subclass is unavailable (e.g. running off-device),
     * this throws and the caller must inject a fake bridge instead.
     *
     * Implementation note: on a real platform build the consuming app would supply a
     * compiled subclass on the classpath named
     * `digital.vasic.helix.ota.bridge.android.PlatformUpdateEngineCallback`. We look it
     * up reflectively to keep THIS module free of any @SystemApi compile dependency.
     */
    fun makeCallback(delegate: RawEngineCallback): Any {
        val subclassName = "digital.vasic.helix.ota.bridge.android.PlatformUpdateEngineCallback"
        val subclass = Class.forName(subclassName, true, callbackClass.classLoader)
        return subclass
            .getConstructor(RawEngineCallback::class.java)
            .newInstance(delegate)
    }

    companion object {
        /** True if the @SystemApi UpdateEngine classes resolve on this runtime. */
        fun isAvailable(
            classLoader: ClassLoader = ReflectiveUpdateEngine::class.java.classLoader!!,
        ): Boolean = try {
            Class.forName("android.os.UpdateEngine", false, classLoader)
            true
        } catch (_: Throwable) {
            false
        }

        /**
         * Generic reflective invoke helper, exposed for the boot-state observer and
         * tests. Uses a [Proxy]-based [InvocationHandler] only where the target is an
         * interface.
         */
        internal fun proxyInterface(
            iface: Class<*>,
            handler: (Method, Array<Any?>?) -> Any?,
        ): Any = Proxy.newProxyInstance(
            iface.classLoader,
            arrayOf(iface),
            InvocationHandler { _, method, args -> handler(method, args) },
        )
    }
}
