package com.shade.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import java.io.File

/**
 * Yuvarlak avatar: yerel dosya varsa fotoğraf gösterir, yoksa baş harf gösterir.
 *
 * @param imagePath  Yerel dosya yolu (contact.profileImagePath veya kendi fotoğrafı)
 * @param fallbackLetter Fotoğraf yoksa gösterilecek harf (genellikle ismin ilk harfi)
 * @param size       Avatar boyutu
 * @param backgroundColor Arka plan rengi (fotoğraf yokken)
 * @param textColor  Harf rengi
 * @param fontSize   Harf boyutu
 */
@Composable
fun AvatarImage(
    imagePath: String?,
    fallbackLetter: String,
    size: Dp = 52.dp,
    backgroundColor: Color = Color(0xFF7B2FBE),
    textColor: Color = Color.White,
    fontSize: TextUnit = 20.sp,
    modifier: Modifier = Modifier
) {
    val file = imagePath?.let { File(it) }
    val hasImage = file != null && file.exists()

    if (hasImage) {
        AsyncImage(
            model = file,
            contentDescription = null,
            modifier = modifier
                .size(size)
                .clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Surface(
            modifier = modifier.size(size),
            shape = CircleShape,
            color = backgroundColor
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = fallbackLetter.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = fontSize
                    ),
                    color = textColor
                )
            }
        }
    }
}
