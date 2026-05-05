package com.example.barandgrillpos.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CustomerDto(
    val id: String? = null,
    val name: String,
    val phone: String,
    val email: String? = null,
    val address: String? = null,
    @SerialName("id_type") val idType: String? = null,
    @SerialName("id_number") val idNumber: String? = null,
    @SerialName("profile_image_url") val profileImageUrl: String? = null,
    @SerialName("membership_status") val membershipStatus: String = "ACTIVE",
    @SerialName("membership_expiry") val membershipExpiry: String? = null,
    val notes: String? = null,
    @SerialName("branch_id") val branchId: String? = null,
    val created_at: String? = null
)
