class SetterFluent {
	int nonfluent;
	int fluent;
	int nonfluent_accessLevel;
	int fluent_accessLevel;
	
	public void setNonfluent(final int nonfluent) {
		this.nonfluent = nonfluent;
	}
	public SetterFluent fluent(final int fluent) {
		this.fluent = fluent;
		return this;
	}
	private void setNonfluent_accessLevel(final int nonfluent_accessLevel) {
		this.nonfluent_accessLevel = nonfluent_accessLevel;
	}
	private SetterFluent fluent_accessLevel(final int fluent_accessLevel) {
		this.fluent_accessLevel = fluent_accessLevel;
		return this;
	}
}