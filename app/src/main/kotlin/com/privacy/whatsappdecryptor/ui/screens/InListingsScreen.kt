package com.privacy.whatsappdecryptor.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.privacy.whatsappdecryptor.core.inventory.ImportantDealerRow
import com.privacy.whatsappdecryptor.core.inventory.ManagedListing
import com.privacy.whatsappdecryptor.core.inventory.ProjectRegistry
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val ListingDateFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy • h:mm a")
    .withZone(ZoneId.systemDefault())

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InListingsScreen(
    listings: List<ManagedListing>,
    isLoading: Boolean,
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit,
    selectedSociety: String?,
    onSocietyFilterChanged: (String?) -> Unit,
    onUpdateListing: (id: String, updatedRow: ImportantDealerRow) -> Unit,
    onDeleteListing: (id: String) -> Unit,
    onAddListing: (row: ImportantDealerRow) -> Unit,
    onRefresh: () -> Unit,
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    var listingToEdit by remember { mutableStateOf<ManagedListing?>(null) }
    var listingToDelete by remember { mutableStateOf<ManagedListing?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Collect available unique project names from current listings + registry for quick chips
    val availableSocieties = remember(listings) {
        listings.map { it.row.society }.filter { it.isNotBlank() }.distinct().sorted()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Target (IN) Listings",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "${listings.size} active listings • Real-time export synced",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh Listings")
                    }
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddDialog = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Listing") },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChanged,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search dealer, phone, BHK, notes…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChanged("") }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp)
            )

            // Society quick filter chips row
            if (availableSocieties.isNotEmpty()) {
                val scrollState = rememberScrollState()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(scrollState)
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = selectedSociety == null,
                        onClick = { onSocietyFilterChanged(null) },
                        label = { Text("All (${listings.size})") }
                    )
                    availableSocieties.forEach { society ->
                        val count = listings.count { it.row.society.equals(society, ignoreCase = true) }
                        FilterChip(
                            selected = selectedSociety.equals(society, ignoreCase = true),
                            onClick = {
                                if (selectedSociety.equals(society, ignoreCase = true)) {
                                    onSocietyFilterChanged(null)
                                } else {
                                    onSocietyFilterChanged(society)
                                }
                            },
                            label = { Text("$society ($count)") }
                        )
                    }
                }
            }

            if (isLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (listings.isEmpty() && !isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Default.FormatListBulleted,
                                    contentDescription = null,
                                    modifier = Modifier.size(36.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "No 'IN' Listings Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = if (searchQuery.isNotEmpty() || selectedSociety != null) {
                                "No listings match your filter criteria. Try clearing search or filter."
                            } else {
                                "No listings have been extracted yet. You can scan chats or add new listings manually."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp)
                        )
                        Button(
                            onClick = { showAddDialog = true },
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add First Listing")
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(listings, key = { it.id }) { item ->
                        ListingCard(
                            item = item,
                            onEdit = { listingToEdit = item },
                            onDelete = { listingToDelete = item }
                        )
                    }
                }
            }
        }
    }

    // Add Listing Dialog
    if (showAddDialog) {
        ListingEditDialog(
            title = "Add Target Listing",
            initialRow = ImportantDealerRow(
                society = selectedSociety ?: ProjectRegistry.SELECTED_PROJECT_NAMES.firstOrNull() ?: "",
                projectListStatus = "IN",
                sec = "",
                area = "",
                acco = "3 BHK",
                floor = "",
                flatNo = "",
                dealerName = "",
                phoneNo = "",
                price = "",
                fullMessage = "",
                isDuplicate = false
            ),
            onDismiss = { showAddDialog = false },
            onConfirm = { newRow ->
                onAddListing(newRow)
                showAddDialog = false
            }
        )
    }

    // Edit Listing Dialog
    listingToEdit?.let { target ->
        ListingEditDialog(
            title = "Edit Listing",
            initialRow = target.row,
            onDismiss = { listingToEdit = null },
            onConfirm = { updatedRow ->
                onUpdateListing(target.id, updatedRow)
                listingToEdit = null
            }
        )
    }

    // Delete Confirmation Dialog
    listingToDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { listingToDelete = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Listing?") },
            text = {
                Text(
                    "Are you sure you want to delete this listing for \"${target.row.society}\"?\n\n" +
                    "It will be permanently removed from all future Sub-Excels, ZIP exports, and Master CSVs."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteListing(target.id)
                        listingToDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { listingToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun ListingCard(
    item: ManagedListing,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val row = item.row
    var expanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            // Header Row: Society & Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = row.society.ifEmpty { "UNSPECIFIED" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (row.price.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = if (row.price.startsWith("₹") || row.price.startsWith("Rs", ignoreCase = true)) {
                                row.price
                            } else {
                                "₹${row.price}"
                            },
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            // Property details chips: BHK, Area, Floor, Sector/Flat
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (row.acco.isNotBlank()) {
                    DetailChip(text = row.acco, icon = Icons.Default.Apartment)
                }
                if (row.area.isNotBlank()) {
                    DetailChip(text = row.area, icon = null)
                }
                if (row.floor.isNotBlank()) {
                    DetailChip(text = "${row.floor} Flr", icon = null)
                }
                if (row.flatNo.isNotBlank()) {
                    DetailChip(text = "Unit ${row.flatNo}", icon = null)
                }
                if (row.sec.isNotBlank()) {
                    DetailChip(text = "Sec ${row.sec}", icon = Icons.Default.LocationOn)
                }
            }

            Spacer(Modifier.height(10.dp))

            // Dealer Info Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = buildString {
                            append(row.dealerName.ifBlank { "Dealer" })
                            if (row.phoneNo.isNotBlank()) {
                                append(" • ${row.phoneNo}")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (row.isDuplicate) {
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "Repost",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            // Raw message snippet if available
            if (row.fullMessage.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = row.fullMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f),
                    maxLines = if (expanded) Int.MAX_VALUE else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        .clickable { expanded = !expanded }
                        .padding(8.dp)
                )
            }

            // Timestamp and actions row
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val formattedDate = remember(item.timestamp) {
                    if (item.timestamp > 0) {
                        runCatching {
                            ListingDateFormatter.format(Instant.ofEpochMilli(item.timestamp))
                        }.getOrDefault("")
                    } else ""
                }
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = onEdit,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Edit")
                    }

                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Delete")
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
        shape = RoundedCornerShape(6.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            if (icon != null) {
                Icon(
                    icon,
                    contentDescription = null,
                    modifier = Modifier.size(12.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingEditDialog(
    title: String,
    initialRow: ImportantDealerRow,
    onDismiss: () -> Unit,
    onConfirm: (ImportantDealerRow) -> Unit
) {
    var society by remember { mutableStateOf(initialRow.society) }
    var acco by remember { mutableStateOf(initialRow.acco) }
    var price by remember { mutableStateOf(initialRow.price) }
    var area by remember { mutableStateOf(initialRow.area) }
    var floor by remember { mutableStateOf(initialRow.floor) }
    var flatNo by remember { mutableStateOf(initialRow.flatNo) }
    var sec by remember { mutableStateOf(initialRow.sec) }
    var dealerName by remember { mutableStateOf(initialRow.dealerName) }
    var phoneNo by remember { mutableStateOf(initialRow.phoneNo) }
    var fullMessage by remember { mutableStateOf(initialRow.fullMessage) }

    var expandedSocietyDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Project / Society with dropdown suggestions
                ExposedDropdownMenuBox(
                    expanded = expandedSocietyDropdown,
                    onExpandedChange = { expandedSocietyDropdown = it }
                ) {
                    OutlinedTextField(
                        value = society,
                        onValueChange = {
                            society = it
                            expandedSocietyDropdown = true
                        },
                        label = { Text("Project / Society *") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expandedSocietyDropdown) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth(),
                        singleLine = true
                    )

                    val matching = remember(society) {
                        if (society.isBlank()) {
                            ProjectRegistry.SELECTED_PROJECT_NAMES.take(10)
                        } else {
                            ProjectRegistry.SELECTED_PROJECT_NAMES.filter {
                                it.contains(society.trim(), ignoreCase = true)
                            }.take(10)
                        }
                    }

                    if (matching.isNotEmpty()) {
                        ExposedDropdownMenu(
                            expanded = expandedSocietyDropdown,
                            onDismissRequest = { expandedSocietyDropdown = false }
                        ) {
                            matching.forEach { proj ->
                                DropdownMenuItem(
                                    text = { Text(proj) },
                                    onClick = {
                                        society = proj
                                        expandedSocietyDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = acco,
                        onValueChange = { acco = it },
                        label = { Text("BHK / Acco") },
                        placeholder = { Text("e.g. 3 BHK") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = price,
                        onValueChange = { price = it },
                        label = { Text("Price") },
                        placeholder = { Text("e.g. 2.5 Cr") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = area,
                        onValueChange = { area = it },
                        label = { Text("Area") },
                        placeholder = { Text("e.g. 1850 sqft") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = floor,
                        onValueChange = { floor = it },
                        label = { Text("Floor") },
                        placeholder = { Text("e.g. 14th") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = flatNo,
                        onValueChange = { flatNo = it },
                        label = { Text("Unit / Flat No") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = sec,
                        onValueChange = { sec = it },
                        label = { Text("Sector") },
                        placeholder = { Text("e.g. 113") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = dealerName,
                        onValueChange = { dealerName = it },
                        label = { Text("Dealer Name") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = phoneNo,
                        onValueChange = { phoneNo = it },
                        label = { Text("Phone No") },
                        placeholder = { Text("10 digits") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                }

                OutlinedTextField(
                    value = fullMessage,
                    onValueChange = { fullMessage = it },
                    label = { Text("Original Message / Notes") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val cleanSoc = society.trim().uppercase()
                    val updated = initialRow.copy(
                        society = cleanSoc,
                        projectListStatus = "IN",
                        acco = acco.trim(),
                        price = price.trim(),
                        area = area.trim(),
                        floor = floor.trim(),
                        flatNo = flatNo.trim(),
                        sec = sec.trim(),
                        dealerName = dealerName.trim().ifEmpty { "Dealer" },
                        phoneNo = phoneNo.trim(),
                        fullMessage = fullMessage.trim()
                    )
                    onConfirm(updated)
                },
                enabled = society.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
