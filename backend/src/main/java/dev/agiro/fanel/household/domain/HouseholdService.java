package dev.agiro.fanel.household.domain;

import dev.agiro.fanel.household.api.HouseholdApi;
import dev.agiro.fanel.household.api.HouseholdCreated;
import dev.agiro.fanel.household.api.HouseholdDto;
import dev.agiro.fanel.household.api.MemberDeleted;
import dev.agiro.fanel.household.api.MemberDto;
import dev.agiro.fanel.household.api.MemberRole;
import dev.agiro.fanel.household.infra.HouseholdRepository;
import dev.agiro.fanel.household.infra.MemberRepository;
import dev.agiro.fanel.shared.web.ConflictException;
import dev.agiro.fanel.shared.web.EntityNotFoundException;
import dev.agiro.fanel.shared.web.ForbiddenException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
@Transactional
public class HouseholdService implements HouseholdApi {
    private final HouseholdRepository households;
    private final MemberRepository members;
    private final ApplicationEventPublisher events;
    private final PasswordEncoder passwordEncoder;

    public HouseholdService(HouseholdRepository households, MemberRepository members,
                            ApplicationEventPublisher events, PasswordEncoder passwordEncoder) {
        this.households = households;
        this.members = members;
        this.events = events;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public HouseholdDto create(String name, String locale, String timezone) {
        Household household = households.save(new Household(name, locale, timezone));
        events.publishEvent(new HouseholdCreated(household.getId()));
        return toDto(household);
    }

    @Override
    @Transactional(readOnly = true)
    public List<HouseholdDto> list() {
        return households.findAll().stream().map(HouseholdService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public HouseholdDto get(UUID id) {
        return toDto(find(id));
    }

    @Override
    public MemberDto addMember(UUID householdId, String name, MemberRole role, String color) {
        Household household = find(householdId);
        return toDto(members.save(household.addMember(name, role, color)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberDto> listMembers(UUID householdId) {
        find(householdId);
        return members.findAllByHouseholdId(householdId).stream().map(HouseholdService::toDto).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public MemberDto getMember(UUID householdId, UUID memberId) {
        return toDto(findMember(householdId, memberId));
    }

    @Override
    public MemberDto updateMember(UUID householdId, UUID memberId, String name, String color) {
        Member member = findMember(householdId, memberId);
        if (name != null && !name.isBlank()) member.setName(name);
        if (color != null) member.setColor(color);
        return toDto(members.save(member));
    }

    @Override
    public void deleteMember(UUID householdId, UUID memberId) {
        Member member = findMember(householdId, memberId);
        members.deleteGuardianLinks(memberId.toString());
        members.delete(member);
        events.publishEvent(new MemberDeleted(householdId, memberId));
    }

    @Override
    public MemberDto setMemberPin(UUID householdId, UUID memberId, String pin) {
        Member member = findMember(householdId, memberId);
        member.setPin(pin);
        return toDto(members.save(member));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean verifyMemberPin(UUID householdId, UUID memberId, String pin) {
        return findMember(householdId, memberId).matchesPin(pin);
    }

    @Override
    public MemberDto setMemberCredentials(UUID householdId, UUID memberId, String username, String rawPassword) {
        Member member = findMember(householdId, memberId);
        members.findByUsername(username)
                .filter(existing -> !existing.getId().equals(memberId))
                .ifPresent(existing -> { throw new ConflictException("Username already in use: " + username); });
        member.setCredentials(username, passwordEncoder.encode(rawPassword));
        return toDto(members.save(member));
    }

    @Override
    public MemberDto setMemberRole(UUID householdId, UUID memberId, MemberRole role) {
        Member member = findMember(householdId, memberId);
        member.setRole(role);
        return toDto(members.save(member));
    }

    @Override
    public MemberDto setMemberGuardians(UUID householdId, UUID childId, List<UUID> guardianIds) {
        Member child = findMember(householdId, childId);
        if (child.getRole() != MemberRole.CHILD) {
            throw new IllegalArgumentException("Only children can have guardians");
        }
        Set<Member> guardians = new HashSet<>();
        for (UUID guardianId : guardianIds) {
            Member guardian = findMember(householdId, guardianId);
            if (guardian.getRole() == MemberRole.CHILD) {
                throw new IllegalArgumentException("A child cannot be a guardian: " + guardianId);
            }
            guardians.add(guardian);
        }
        child.setGuardians(guardians);
        return toDto(members.save(child));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> relatedChildIds(UUID householdId, UUID memberId) {
        return findMember(householdId, memberId).wardIds();
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isSetupRequired() {
        return households.count() == 0;
    }

    @Override
    public synchronized MemberDto bootstrap(String householdName, String locale, String timezone,
                                            String memberName, String username, String rawPassword) {
        if (!isSetupRequired()) {
            throw new ForbiddenException("Setup has already been completed");
        }
        Household household = households.save(new Household(householdName, locale, timezone));
        events.publishEvent(new HouseholdCreated(household.getId()));
        Member member = members.save(household.addMember(memberName, MemberRole.ADMIN, null));
        member.setCredentials(username, passwordEncoder.encode(rawPassword));
        return toDto(members.save(member));
    }

    private Member findMember(UUID householdId, UUID memberId) {
        return members.findById(memberId)
                .filter(m -> m.getHousehold().getId().equals(householdId))
                .orElseThrow(() -> new EntityNotFoundException("Member not found: " + memberId));
    }

    private Household find(UUID id) {
        return households.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Household not found: " + id));
    }

    private static HouseholdDto toDto(Household household) {
        return new HouseholdDto(household.getId(), household.getName(), household.getLocale(),
                household.getTimezone(), household.getCreatedAt());
    }

    private static MemberDto toDto(Member member) {
        return new MemberDto(member.getId(), member.getHousehold().getId(), member.getName(),
                member.getRole(), member.getColor(), member.getCreatedAt(), member.getUsername(),
                member.guardianIds(), member.hasPin(), member.hasCredentials());
    }
}
