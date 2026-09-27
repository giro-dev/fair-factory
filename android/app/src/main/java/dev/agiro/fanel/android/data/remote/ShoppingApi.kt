package dev.agiro.fanel.android.data.remote

import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface ShoppingApi {
    @GET("/api/v1/households/{householdId}/shopping/lists")
    suspend fun listLists(@Path("householdId") householdId: String): List<ShoppingListDto>

    @POST("/api/v1/households/{householdId}/shopping/lists")
    suspend fun createList(
        @Path("householdId") householdId: String,
        @Body request: ListNameRequest
    ): ShoppingListDto

    @GET("/api/v1/households/{householdId}/shopping/lists/{listId}")
    suspend fun getList(
        @Path("householdId") householdId: String,
        @Path("listId") listId: String
    ): ShoppingListDto

    @PUT("/api/v1/households/{householdId}/shopping/lists/{listId}")
    suspend fun updateList(
        @Path("householdId") householdId: String,
        @Path("listId") listId: String,
        @Body request: ListNameRequest
    ): ShoppingListDto

    @DELETE("/api/v1/households/{householdId}/shopping/lists/{listId}")
    suspend fun deleteList(
        @Path("householdId") householdId: String,
        @Path("listId") listId: String
    )

    @POST("/api/v1/households/{householdId}/shopping/lists/{listId}/items")
    suspend fun addItem(
        @Path("householdId") householdId: String,
        @Path("listId") listId: String,
        @Body request: AddItemRequest
    ): ShoppingItemDto

    @PATCH("/api/v1/households/{householdId}/shopping/items/{itemId}")
    suspend fun updateItem(
        @Path("householdId") householdId: String,
        @Path("itemId") itemId: String,
        @Body request: UpdateItemRequest
    ): ShoppingItemDto

    @DELETE("/api/v1/households/{householdId}/shopping/items/{itemId}")
    suspend fun removeItem(
        @Path("householdId") householdId: String,
        @Path("itemId") itemId: String
    )

    @POST("/api/v1/households/{householdId}/shopping/lists/{listId}/clear-purchased")
    suspend fun clearPurchased(
        @Path("householdId") householdId: String,
        @Path("listId") listId: String
    ): Map<String, Int>
}
