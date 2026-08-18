package com.sohan.diutransportschedule.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sohan.diutransportschedule.ui.theme.CardSurfaceLight
import com.sohan.diutransportschedule.ui.theme.TimeChipBorderLight

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutPage(
    onBack: () -> Unit
) {
    LocalContext.current
    val surfaceColor = MaterialTheme.colorScheme.surface
    val dark =
        (surfaceColor.red * 0.299f + surfaceColor.green * 0.587f + surfaceColor.blue * 0.114f) < 0.5f
    val premiumLightBorder = TimeChipBorderLight
    BackHandler(onBack = onBack)
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "About",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.Top
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
                colors = CardDefaults.cardColors(
                    containerColor = if (dark) {
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
                    } else {
                        CardSurfaceLight
                    }
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "DIU Transport Schedule",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = "This transport app is designed to make daily commuting easier and more efficient. It helps users find the best routes, estimate travel time, and access important transport information in one place.\n" +
                                "\n" +
                                "The app includes features such as route suggestions, fare estimation, and simple navigation, making it useful for students, professionals, and regular commuters.\n" +
                                "\n" +
                                "The main objective of this project is to reduce travel difficulties and provide a smarter way to navigate transportation systems. This app was developed as part of a software engineering project to apply practical problem-solving skills.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

//            Spacer(modifier = Modifier.height(10.dp))
//
//            Card(
//                modifier = Modifier.fillMaxWidth(),
//                shape = RoundedCornerShape(20.dp),
//                elevation = CardDefaults.cardElevation(defaultElevation = if (dark) 0.dp else 2.dp),
//                border = if (dark) null else BorderStroke(1.dp, premiumLightBorder),
//                colors = CardDefaults.cardColors(
//                    containerColor = if (dark) {
//                        MaterialTheme.colorScheme.surface.copy(alpha = 0.7f)
//                    } else {
//                        CardSurfaceLight
//                    }
//                )
//            ){
//                Column(
//                    modifier = Modifier
//                        .fillMaxWidth()
//                        .padding(20.dp),
//                    verticalArrangement = Arrangement.spacedBy(10.dp)
//                ) {
//                    Text(
//                        text = "Contact US",
//                        style = MaterialTheme.typography.titleLarge,
//                        fontWeight = FontWeight.Bold,
//                        color = MaterialTheme.colorScheme.onSurface
//                    )
//
//                    Spacer(modifier = Modifier.height(1.dp))
//
//                    Text(
//                        text = "Facebook ID: Sohan Pavres (click here)",
//                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
//                        color = MaterialTheme.colorScheme.onSurface,
//                        modifier = Modifier.clickable {
//                            val url = "https://www.facebook.com/sohanParves9"
//                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                            context.startActivity(intent)
//                        }
//                    )
//
//                    Spacer(modifier = Modifier.height(0.dp))
//
//                    Text(
//                        text = "WhatsApp Number: 01615268900 (click here)",
//                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
//                        color = MaterialTheme.colorScheme.onSurface,
//                        modifier = Modifier.clickable {
//                            val number = "8801615268900"
//                            val url = "https://wa.me/$number"
//                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
//                            context.startActivity(intent)
//                        }
//                    )
//
//                }
//            }
        }
    }
}