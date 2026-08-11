package com.sakena.membership.domain

import com.sakena.membership.domain.model.BuildingInvitation

/**
 * Outbound port for delivering an invitation to its recipient. Keeps mail (and
 * later SMS) out of the domain and application layers.
 */
interface InvitationNotifier {
    /**
     * Delivers the invitation link. Implementations must not throw on a
     * delivery failure — the invitation is still valid and its link can always
     * be shared manually by the manager.
     */
    fun notify(invitation: BuildingInvitation, buildingName: String, acceptUrl: String)
}
