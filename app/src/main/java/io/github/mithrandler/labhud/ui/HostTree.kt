package io.github.mithrandler.labhud.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.mithrandler.labhud.data.ZoneHost
import io.github.mithrandler.labhud.data.line
import io.github.mithrandler.labhud.data.tone

/**
 * A Proxmox-style tree of the Docker hosts: a host row that opens its tabs as children.
 */
@Composable
internal fun HostTree(
    hosts: List<ZoneHost>,
    host: String,
    tab: String,
    tabs: List<Pair<String, String>>,
    onPick: (host: String, tab: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val dockerHosts = hosts.filter { it.docker }
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(LabScale.space2),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {
        dockerHosts.forEach { h ->
            item(key = h.name) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { onPick(h.name, tab) }
                        .padding(vertical = LabScale.space2),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (h.name == host) "▾" else "▸",
                        fontSize = LabScale.textXs,
                        color = Lab.muted,
                        modifier = Modifier.width(LabScale.space3),
                    )
                    Spacer(Modifier.width(LabScale.space1))
                    Dot(h.tone())
                    Spacer(Modifier.width(LabScale.space2))
                    Text(
                        text = h.name,
                        fontSize = LabScale.textMd,
                        color = Lab.text,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = h.line(),
                        fontSize = LabScale.textXs,
                        color = if (h.containers.bad > 0) Lab.warn else Lab.muted,
                    )
                }
            }
            if (h.name == host) {
                tabs.forEach { (id, label) ->
                    if (id == "vms" && !h.pve) return@forEach
                    item(key = "${h.name}/$id") {
                        val current = id == tab
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    color = if (current) Lab.card else androidx.compose.ui.graphics.Color.Transparent,
                                    shape = if (current) RoundedCornerShape(LabScale.radiusInner) else RoundedCornerShape(0.dp),
                                )
                                .clickable { onPick(h.name, id) }
                                .padding(start = LabScale.space6, top = LabScale.space2, bottom = LabScale.space2),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (current) {
                                Box(Modifier.width(2.dp).height(LabScale.space6).background(Lab.accent))
                                Spacer(Modifier.width(LabScale.space2))
                            }
                            Text(
                                text = label,
                                fontSize = LabScale.textSm,
                                color = if (current) Lab.text else Lab.sub,
                                fontWeight = if (current) FontWeight.SemiBold else FontWeight.Normal,
                            )
                        }
                    }
                }
            }
        }
    }
}
