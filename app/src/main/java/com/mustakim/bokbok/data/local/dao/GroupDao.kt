package com.mustakim.bokbok.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.mustakim.bokbok.data.local.entity.GroupEntity
import com.mustakim.bokbok.data.local.entity.GroupMemberEntity
import com.mustakim.bokbok.data.local.entity.GroupMessageEntity
import com.mustakim.bokbok.data.local.entity.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface GroupDao {
    
    // ============= GROUP OPERATIONS =============
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertGroup(group: GroupEntity)
    
    @Query("SELECT * FROM chat_groups WHERE id = :groupId")
    suspend fun getGroupById(groupId: String): GroupEntity?
    
    @Query("SELECT * FROM chat_groups WHERE id = :groupId")
    fun observeGroup(groupId: String): Flow<GroupEntity?>
    
    @Query("SELECT * FROM chat_groups ORDER BY last_message_time DESC")
    fun getAllGroups(): Flow<List<GroupEntity>>
    
    @Query("UPDATE chat_groups SET name = :name, image_url = :imageUrl WHERE id = :groupId")
    suspend fun updateGroupInfo(groupId: String, name: String, imageUrl: String)
    
    @Query("UPDATE chat_groups SET last_message_text = :text, last_message_time = :time WHERE id = :groupId")
    suspend fun updateLastMessage(groupId: String, text: String, time: Long)
    
    // ============= GROUP MEMBER OPERATIONS =============
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMember(member: GroupMemberEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(members: List<GroupMemberEntity>)
    
    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    fun observeMembers(groupId: String): Flow<List<GroupMemberEntity>>
    
    @Query("SELECT * FROM group_members WHERE group_id = :groupId")
    suspend fun getMembersByGroupId(groupId: String): List<GroupMemberEntity>
    
    @Query("DELETE FROM group_members WHERE group_id = :groupId AND user_id NOT IN (:currentMemberIds)")
    suspend fun removeOldMembers(groupId: String, currentMemberIds: List<String>)
    
    // ============= GROUP MESSAGE OPERATIONS =============
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: GroupMessageEntity)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<GroupMessageEntity>)
    
    @Query("""
        SELECT * FROM group_messages 
        WHERE group_id = :groupId 
        ORDER BY timestamp DESC 
        LIMIT :limit
    """)
    fun getMessagesByGroupId(groupId: String, limit: Int = 50): Flow<List<GroupMessageEntity>>
    
    @Query("SELECT * FROM group_messages WHERE id = :messageId")
    suspend fun getMessageById(messageId: String): GroupMessageEntity?
    
    @Query("SELECT * FROM group_messages WHERE sync_status = :status")
    suspend fun getMessagesBySyncStatus(status: SyncStatus): List<GroupMessageEntity>
    
    @Query("UPDATE group_messages SET sync_status = :status WHERE id = :messageId")
    suspend fun updateSyncStatus(messageId: String, status: SyncStatus)
    
    @Query("UPDATE group_messages SET reactions = :reactions WHERE id = :messageId")
    suspend fun updateReactions(messageId: String, reactions: String)
    
    @Query("UPDATE group_messages SET is_deleted_for_everyone = 1, text = 'Message unsent' WHERE id = :messageId")
    suspend fun markAsDeletedForEveryone(messageId: String)
    
    @Query("DELETE FROM group_messages WHERE id = :messageId")
    suspend fun deleteById(messageId: String)
    
    @Query("""
        UPDATE group_messages 
        SET read_by = :readBy 
        WHERE group_id = :groupId AND sender_id != :currentUserId
    """)
    suspend fun markAllAsRead(groupId: String, currentUserId: String, readBy: String)
    
    // ============= TRANSACTION OPERATIONS =============
    
    @Transaction
    suspend fun saveGroupWithMembers(group: GroupEntity, members: List<GroupMemberEntity>) {
        insertGroup(group)
        insertMembers(members)
    }
}
