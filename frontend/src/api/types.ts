import type { components } from './schema'

// The OpenAPI spec does not mark server-owned fields as required/nullable (Java records
// have no required list and JPA columns can be null), so the generated types are looser
// than reality. These aliases tighten `id` (always present) and declare the fields the
// backend may return as null.
export type AutomationRule = components['schemas']['AutomationRuleDto'] & {
  id: string
  lastRunAt?: string | null
}
export type RuleType = NonNullable<AutomationRule['type']>
export type CalendarEvent = components['schemas']['CalendarEventDto'] & {
  id: string
  time?: string | null
  durationMinutes?: number | null
  source?: 'LOCAL' | 'ICS'
  subscriptionId?: string | null
}
export type CalendarSubscription = components['schemas']['CalendarSubscriptionDto'] & {
  id: string
  color?: string | null
  lastSyncedAt?: string | null
  lastError?: string | null
}
export type Chore = components['schemas']['ChoreDto'] & { id: string }
export type Household = components['schemas']['HouseholdDto'] & { id: string }
export type Member = components['schemas']['MemberDto'] & { id: string }
export type MealPlan = components['schemas']['MealPlanDto']
export type MealSlot = components['schemas']['MealSlotDto']
export type Recipe = components['schemas']['RecipeDto'] & { id: string }
export type ShoppingList = components['schemas']['ShoppingListDto'] & { id: string }
export type ShoppingItem = components['schemas']['ShoppingItemDto'] & { id: string }
export type AgentDefinition = components['schemas']['AgentDefinition']
export type ImportResult = components['schemas']['ImportResult']
