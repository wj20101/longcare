package com.ytone.longcare.features.sales

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ytone.longcare.R
import com.ytone.longcare.features.maindashboard.ui.TopHeader
import com.ytone.longcare.model.CurrentUser
import com.ytone.longcare.model.UserLatentListModel

@Composable
internal fun SalesDashboardScreen(
    user: CurrentUser,
    companyName: String,
    customers: List<UserLatentListModel>,
    toDoCount: Int?,
    isToDoCountLoading: Boolean,
    onRegisterCustomer: () -> Unit,
    onReminders: () -> Unit,
    onCustomerClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier =
            modifier
                .statusBarsPadding()
                .padding(horizontal = 18.dp),
        contentPadding = PaddingValues(top = 10.dp, bottom = 18.dp),
        verticalArrangement = Arrangement.spacedBy(13.dp),
    ) {
        item {
            TopHeader(
                user = user,
                companyName = companyName,
            )
        }
        item {
            Image(
                painter = painterResource(R.drawable.sales_home_banner),
                contentDescription =
                    stringResource(R.string.sales_home_banner_description),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .aspectRatio(2712f / 960f)
                        .clip(RoundedCornerShape(15.dp)),
                contentScale = ContentScale.FillBounds,
            )
        }
        item {
            if (useSalesLargeTextLayout()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_registration,
                        title = stringResource(R.string.sales_home_registration_title),
                        subtitle =
                            stringResource(R.string.sales_home_registration_subtitle),
                        onClick = onRegisterCustomer,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_signin,
                        title = stringResource(R.string.sales_home_reminder_title),
                        subtitle =
                            toDoCount.toSalesToDoSubtitle(isToDoCountLoading),
                        onClick = onReminders,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_registration,
                        title = stringResource(R.string.sales_home_registration_title),
                        subtitle =
                            stringResource(R.string.sales_home_registration_subtitle),
                        onClick = onRegisterCustomer,
                        modifier = Modifier.weight(1f),
                    )
                    SalesHomeFeatureCard(
                        iconRes = R.drawable.sales_home_signin,
                        title = stringResource(R.string.sales_home_reminder_title),
                        subtitle =
                            toDoCount.toSalesToDoSubtitle(isToDoCountLoading),
                        onClick = onReminders,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        item {
            Text(
                text = stringResource(R.string.sales_home_latest_customers),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 15.dp),
                color = Color.Black,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        if (customers.isEmpty()) {
            item {
                Column(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .heightIn(min = 82.dp)
                            .salesWhiteCard()
                            .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.sales_home_no_customers),
                        color = SalesTextPrimary,
                        fontSize = 16.sp,
                    )
                    Text(
                        text = stringResource(R.string.sales_home_no_customers_hint),
                        color = SalesTextSecondary,
                        fontSize = 14.sp,
                    )
                }
            }
        } else {
            items(
                items = customers.take(4),
                key = UserLatentListModel::id,
            ) { customer ->
                SalesLatestCustomerCard(
                    customer = customer,
                    onClick = { onCustomerClick(customer.id) },
                )
            }
        }
    }
}

@Composable
private fun SalesHomeFeatureCard(
    @DrawableRes iconRes: Int,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .heightIn(min = 70.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFFF3F7FE))
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        SalesFeatureIcon(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = SalesTextPrimary,
                fontSize = 17.sp,
                lineHeight = 22.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                color = SalesTextSecondary,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SalesLatestCustomerCard(
    customer: UserLatentListModel,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .salesWhiteCard()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text =
                        customer.userName.ifBlank {
                            stringResource(R.string.sales_customer_unnamed)
                        },
                    modifier = Modifier.weight(1f),
                    color = SalesTextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = customer.checkState.toSalesCheckStateLabel(),
                    color = customer.checkState.toSalesCheckStateColor(),
                    fontSize = 14.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text =
                    stringResource(
                        R.string.sales_customer_address_format,
                        customer.liveAddress.ifBlank {
                            stringResource(R.string.sales_customer_address_value_pending)
                        },
                    ),
                color = SalesTextSecondary,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = stringResource(R.string.sales_customer_view_description),
            tint = Color(0xFFB9C9DD),
        )
    }
}

@Composable
private fun Int?.toSalesToDoSubtitle(isLoading: Boolean): String =
    when {
        this != null && this > 0 ->
            stringResource(R.string.sales_todo_count_format, this)

        this == 0 -> stringResource(R.string.sales_todo_none)
        isLoading -> stringResource(R.string.sales_todo_loading)
        else -> stringResource(R.string.sales_todo_open_hint)
    }
