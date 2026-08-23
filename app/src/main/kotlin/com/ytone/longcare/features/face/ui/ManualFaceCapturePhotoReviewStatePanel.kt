package com.ytone.longcare.features.face.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ytone.longcare.R

@Composable
internal fun PhotoReviewStatePanel(
    currentState: ManualFaceCaptureState,
    detectedFaces: List<DetectedFace>,
    selectedFaceIndex: Int?,
    onFaceSelected: (Int) -> Unit,
    onFaceLongClick: (DetectedFace) -> Unit,
    onRetakePhoto: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            when (currentState) {
                is ManualFaceCaptureState.NoFacesDetected -> {
                    Text(
                        text = stringResource(R.string.face_capture_no_face),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.face_capture_quality_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                }

                is ManualFaceCaptureState.FacesDetected -> {
                    Text(
                        text = stringResource(
                            R.string.face_capture_face_count,
                            detectedFaces.size,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        itemsIndexed(detectedFaces) { index, face ->
                            FaceSelectionItem(
                                face = face,
                                isSelected = index == selectedFaceIndex,
                                onClick = { onFaceSelected(index) },
                                onLongClick = { onFaceLongClick(face) }
                            )
                        }
                    }
                }

                else -> {
                    if (detectedFaces.size == 1) {
                        Text(
                            text = stringResource(R.string.face_capture_face_detected),
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            PhotoReviewRetakeButtonRow(onRetakePhoto = onRetakePhoto)
        }
    }
}
