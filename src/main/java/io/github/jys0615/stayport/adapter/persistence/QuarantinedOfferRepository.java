package io.github.jys0615.stayport.adapter.persistence;

import io.github.jys0615.stayport.domain.quarantine.QuarantinedOffer;
import org.springframework.data.jpa.repository.JpaRepository;

interface QuarantinedOfferRepository extends JpaRepository<QuarantinedOffer, Long> {
}
