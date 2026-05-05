package com.example.barandgrillownerpanel.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.barandgrillownerpanel.models.BranchDto
import com.example.barandgrillownerpanel.ui.theme.*

/**
 * Horizontal pill/chip row for selecting a branch.
 * [selectedBranch] = null means GENERAL (all branches combined).
 */
@Composable
fun BranchFilterBar(
    branches: List<BranchDto>,
    selectedBranch: BranchDto?,
    onBranchChange: (BranchDto?) -> Unit,
    modifier: Modifier = Modifier
) {
    if (branches.isEmpty()) return

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // GENERAL chip
        BranchChip(
            label = "🌐 GENERAL",
            isSelected = selectedBranch == null,
            onClick = { onBranchChange(null) }
        )
        // One chip per branch
        branches.forEach { branch ->
            BranchChip(
                label = branchIcon(branch) + " " + branch.name,
                isSelected = selectedBranch?.id == branch.id,
                onClick = { onBranchChange(branch) }
            )
        }
    }
}

@Composable
private fun BranchChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) PrimaryOrange else CharcoalGray
    val textColor = if (isSelected) Color.White else TextSecondary

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        modifier = Modifier.height(36.dp)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        ) {
            Text(label, color = textColor, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
        }
    }
}

private fun branchIcon(branch: BranchDto): String = when (branch.type.uppercase()) {
    "LIQUOR_SHOP" -> "🍾"
    else -> "🍖"
}
