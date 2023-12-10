package com.byagowi.persiancalendar.ui.common

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.integerResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.byagowi.persiancalendar.R
import com.byagowi.persiancalendar.entities.CalendarType
import com.byagowi.persiancalendar.entities.EventsStore
import com.byagowi.persiancalendar.entities.Jdn
import com.byagowi.persiancalendar.global.enabledCalendars
import com.byagowi.persiancalendar.global.isAstronomicalExtraFeaturesEnabled
import com.byagowi.persiancalendar.global.isForcedIranTimeEnabled
import com.byagowi.persiancalendar.global.language
import com.byagowi.persiancalendar.global.spacedColon
import com.byagowi.persiancalendar.ui.theme.AppTheme
import com.byagowi.persiancalendar.ui.utils.copyToClipboard
import com.byagowi.persiancalendar.utils.calculateDaysDifference
import com.byagowi.persiancalendar.utils.formatDate
import com.byagowi.persiancalendar.utils.formatDateAndTime
import com.byagowi.persiancalendar.utils.formatNumber
import com.byagowi.persiancalendar.utils.generateZodiacInformation
import com.byagowi.persiancalendar.utils.getA11yDaySummary
import com.byagowi.persiancalendar.utils.isMoonInScorpio
import com.byagowi.persiancalendar.utils.monthName
import com.byagowi.persiancalendar.utils.toGregorianCalendar
import com.byagowi.persiancalendar.utils.toLinearDate
import io.github.cosinekitty.astronomy.seasons
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.Date

class CalendarsView(context: Context, attrs: AttributeSet? = null) : FrameLayout(context, attrs) {
    val root = ComposeView(context)

    init {
        addView(root)
    }

    fun update(jdn: Jdn, selectedCalendar: CalendarType, shownCalendars: List<CalendarType>) {
        root.setContent {
            AppTheme {
                val isExpanded by isExpandedFlow.collectAsState()
                CalendarsOverview(jdn, selectedCalendar, shownCalendars, isExpanded) {
                    isExpandedFlow.value = !isExpanded
                }
            }
        }
    }

    private val isExpandedFlow = MutableStateFlow(false)
    fun toggleExpansion() {
        isExpandedFlow.value = !isExpandedFlow.value
    }
}

@Composable
fun CalendarsOverview(
    jdn: Jdn,
    selectedCalendar: CalendarType,
    shownCalendars: List<CalendarType>,
    isExpanded: Boolean,
    toggleExpansion: () -> Unit
) {
    val context = LocalContext.current
    val isToday by derivedStateOf { Jdn.today() == jdn }
    Column(
        Modifier
            .clickable(
                onClickLabel = stringResource(R.string.more),
                onClick = toggleExpansion,
            )
            .semantics {
                this.contentDescription = getA11yDaySummary(
                    context,
                    jdn,
                    isToday,
                    EventsStore.empty(),
                    withZodiac = true,
                    withOtherCalendars = true,
                    withTitle = true
                )
            },
    ) {
        Spacer(Modifier.height(24.dp))
        val animationTime = integerResource(android.R.integer.config_mediumAnimTime)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 24.dp)
        ) {
            AnimatedVisibility(isAstronomicalExtraFeaturesEnabled && isExpanded) {
                AndroidView(
                    factory = ::MoonView,
                    update = { it.jdn = jdn.value.toFloat() },
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .size(20.dp)
                )
            }
            AnimatedContent(
                if (isToday && isForcedIranTimeEnabled) language.inParentheses.format(
                    jdn.dayOfWeekName, stringResource(R.string.iran_time)
                ) else jdn.dayOfWeekName,
                transitionSpec = {
                    fadeIn(animationSpec = tween(animationTime)).togetherWith(
                        fadeOut(animationSpec = tween(animationTime))
                    )
                },
                label = "weekday name",
            ) { SelectionContainer { Text(it, color = MaterialTheme.colorScheme.primary) } }
        }
        Spacer(Modifier.height(8.dp))
        CalendarsFlow(shownCalendars, jdn)
        Spacer(Modifier.height(4.dp))

        val date by derivedStateOf { jdn.toCalendar(selectedCalendar) }
        val equinox = remember(selectedCalendar, jdn) {
            if (date.month == 12 && date.dayOfMonth >= 20 || date.month == 1 && date.dayOfMonth == 1) {
                val addition = if (date.month == 12) 1 else 0
                val equinoxYear = date.year + addition
                val calendar = Date(
                    seasons(jdn.toCivilDate().year).marchEquinox.toMillisecondsSince1970()
                ).toGregorianCalendar()
                context.getString(
                    R.string.spring_equinox, formatNumber(equinoxYear), calendar.formatDateAndTime()
                )
            } else null
        }
        AnimatedVisibility(visible = equinox != null) {
            SelectionContainer {
                Text(
                    equinox ?: "",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp)
                )
            }
        }

        AnimatedVisibility(!isToday) {
            AnimatedContent(
                listOf(
                    stringResource(R.string.days_distance),
                    spacedColon,
                    calculateDaysDifference(context.resources, jdn)
                ).joinToString(""),
                transitionSpec = {
                    fadeIn(animationSpec = tween(animationTime)).togetherWith(
                        fadeOut(animationSpec = tween(animationTime))
                    )
                },
                label = "diff days",
            ) { state ->
                SelectionContainer {
                    Text(
                        state,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 4.dp)
                    )
                }
            }
        }


        val showIsMoonInScorpio by derivedStateOf {
            if (isAstronomicalExtraFeaturesEnabled) isMoonInScorpio(context, jdn) else ""
        }
        AnimatedVisibility(showIsMoonInScorpio.isNotEmpty()) {
            SelectionContainer {
                Text(
                    showIsMoonInScorpio,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        AnimatedVisibility(isExpanded && isAstronomicalExtraFeaturesEnabled) {
            SelectionContainer {
                Text(
                    generateZodiacInformation(context, jdn, withEmoji = true),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        val startOfYearJdn by derivedStateOf { Jdn(selectedCalendar, date.year, 1, 1) }
        val endOfYearJdn by derivedStateOf { Jdn(selectedCalendar, date.year + 1, 1, 1) - 1 }
        val currentWeek by derivedStateOf { jdn.getWeekOfYear(startOfYearJdn) }
        val weeksCount by derivedStateOf { endOfYearJdn.getWeekOfYear(startOfYearJdn) }
        val progresses = remember(jdn, selectedCalendar) {
            val (seasonPassedDays, seasonDaysCount) = jdn.calculatePersianSeasonPassedDaysAndCount()
            val monthLength = selectedCalendar.getMonthLength(date.year, date.month)
            listOfNotNull(
                Triple(R.string.month, date.dayOfMonth, monthLength),
                Triple(R.string.season, seasonPassedDays, seasonDaysCount),
                Triple(R.string.year, jdn - startOfYearJdn, endOfYearJdn - startOfYearJdn),
            )
        }

        val indicatorValues = progresses.map {
            animateFloatAsState(
                if (isExpanded) it.second.toFloat() / it.third else 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessLow,
                ),
                label = "value"
            ).value
        }
        AnimatedVisibility(isExpanded) {
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.fillMaxWidth(),
            ) {
                progresses.forEachIndexed { i, (stringId, current, max) ->
                    val title = stringResource(stringId)
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .semantics {
                                this.contentDescription = "$title$spacedColon$current / $max"
                            }
                            .padding(all = 8.dp),
                    ) {
                        CircularProgressIndicator(indicatorValues[i])
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(title, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        AnimatedVisibility(isExpanded) {
            val startOfYearText = stringResource(
                R.string.start_of_year_diff,
                formatNumber(jdn - startOfYearJdn + 1),
                formatNumber(currentWeek),
                formatNumber(date.month)
            )
            val endOfYearText = stringResource(
                R.string.end_of_year_diff,
                formatNumber(endOfYearJdn - jdn),
                formatNumber(weeksCount - currentWeek),
                formatNumber(12 - date.month)
            )
            SelectionContainer {
                Text(
                    "$startOfYearText\n$endOfYearText",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 4.dp),
                )
            }
        }

        val angle by animateFloatAsState(if (isExpanded) 180f else 0f, label = "angle")
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            modifier = Modifier
                .rotate(angle)
                .size(16.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary,
        )

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CalendarsFlow(calendarsToShow: List<CalendarType>, jdn: Jdn) {
    @OptIn(ExperimentalLayoutApi::class) FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        val context = LocalContext.current
        val animationTime = integerResource(android.R.integer.config_mediumAnimTime)
        enabledCalendars.forEach { calendarType ->
            AnimatedVisibility(
                visible = calendarType in calendarsToShow,
                enter = fadeIn() + slideInVertically() + expandHorizontally(),
                exit = fadeOut() + slideOutVertically() + shrinkHorizontally(),
            ) {
                AnimatedContent(
                    targetState = jdn,
                    label = "jdn",
                    transitionSpec = {
                        fadeIn(animationSpec = tween(animationTime)).togetherWith(
                            fadeOut(animationSpec = tween(animationTime))
                        )
                    },
                ) { state ->
                    val date = state.toCalendar(calendarType)
                    Column(
                        modifier = Modifier.defaultMinSize(
                            minWidth = dimensionResource(R.dimen.calendar_item_size),
                        ), horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null
                                ) { context.copyToClipboard(formatDate(date)) }
                                .semantics {
                                    this.contentDescription = formatDate(date)
                                },
                        ) {
                            Text(
                                formatNumber(date.dayOfMonth),
                                style = MaterialTheme.typography.displayMedium,
                            )
                            Text(date.monthName)
                        }
                        val linear = date.toLinearDate()
                        Text(
                            linear,
                            modifier = Modifier.clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { context.copyToClipboard(linear) },
                        )
                    }
                }
            }
        }
    }
}
