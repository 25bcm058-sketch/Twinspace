package app.twinspace.icons

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.Drawable
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import app.twinspace.data.db.CloneEntity
import app.twinspace.ui.MainActivity
import java.io.File

/**
 * Generates each clone's home-screen icon: the original app icon with a small
 * colored badge + clone initial in the corner, then pins it as a launcher
 * shortcut that deep-links into MainActivity with the clone id.
 */
class BadgeIconFactory(private val context: Context) {

    fun badgedIcon(clone: CloneEntity, sizePx: Int = 192): Bitmap {
        val base = loadAppIcon(clone.packageName, sizePx)
        val out = base.copy(Bitmap.Config.ARGB_8888, true)
        val canvas = Canvas(out)

        val badgeRadius = sizePx * 0.22f
        val cx = sizePx - badgeRadius * 0.9f
        val cy = sizePx - badgeRadius * 0.9f

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = clone.badgeColor }
        val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = sizePx * 0.03f
        }
        canvas.drawCircle(cx, cy, badgeRadius, fill)
        canvas.drawCircle(cx, cy, badgeRadius, ring)

        val initial = clone.label.firstOrNull()?.uppercase() ?: "?"
        val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = android.graphics.Color.WHITE
            textSize = badgeRadius * 1.1f
            textAlign = Paint.Align.CENTER
            isFakeBoldText = true
        }
        val yOffset = (text.descent() + text.ascent()) / 2
        canvas.drawText(initial, cx, cy - yOffset, text)
        return out
    }

    /** Persists the badged icon and returns its file path (stored on CloneEntity.iconPath). */
    fun saveIcon(clone: CloneEntity): String {
        val file = File(context.filesDir, "icons/${clone.id}.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { badgedIcon(clone).compress(Bitmap.CompressFormat.PNG, 100, it) }
        return file.absolutePath
    }

    /** Pins (or updates) the clone's home-screen shortcut. */
    fun pinShortcut(clone: CloneEntity, iconPath: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = MainActivity.ACTION_LAUNCH_CLONE
            putExtra(MainActivity.EXTRA_CLONE_ID, clone.id)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "clone_${clone.id}")
            .setShortLabel(clone.label)
            .setIcon(IconCompat.createWithBitmap(android.graphics.BitmapFactory.decodeFile(iconPath)))
            .setIntent(intent)
            .setLongLived(true)
            .build()
        ShortcutManagerCompat.pushDynamicShortcut(context, shortcut)
        ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
    }

    fun removeShortcut(cloneId: String) {
        ShortcutManagerCompat.removeDynamicShortcuts(context, listOf("clone_$cloneId"))
    }

    private fun loadAppIcon(packageName: String, sizePx: Int): Bitmap = try {
        context.packageManager.getApplicationIcon(packageName).toBitmapOr(sizePx)
    } catch (e: PackageManager.NameNotFoundException) {
        Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
    }

    private fun Drawable.toBitmapOr(size: Int): Bitmap =
        if (intrinsicWidth > 0) toBitmap(size, size) else Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
}
