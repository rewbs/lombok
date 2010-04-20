class SetterFluentGeneric<T, K> {
	int fluent;
	
	public SetterFluentGeneric<T, K> fluent(final int fluent) {
		this.fluent = fluent;
		return this;
	}
}