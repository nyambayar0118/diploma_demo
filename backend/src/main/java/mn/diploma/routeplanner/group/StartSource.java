package mn.diploma.routeplanner.group;

/** Where a member's start point came from, in the order MemberStartResolver tries them. */
public enum StartSource {
    SHARED_LOCATION, PINNED, SAME_AS_INITIATOR
}
