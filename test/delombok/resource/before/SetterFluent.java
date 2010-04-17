import lombok.Setter;
class SetterFluent {
	@Setter(fluent=false) int nonfluent;
	@Setter(fluent=true) int fluent;
	@Setter(fluent=false, lombok.AccessLevel.PRIVATE) int nonfluent_accessLevel;
	@Setter(fluent=true, lombok.AccessLevel.PRIVATE) int fluent_accessLevel;	
}