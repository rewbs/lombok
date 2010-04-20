class SetterFluentAlreadyExists {
	int x;
	int y;

	public void setX(int x) {
		this.x = x;
	}

	public SetterFluentAlreadyExists y(int y) {
		this.y = y;
		return this;
	}
	
	public SetterFluentAlreadyExists x(final int x) {
		this.x = x;
		return this;
	}

	public void setY(final int y) {
		this.y = y;
	}
}