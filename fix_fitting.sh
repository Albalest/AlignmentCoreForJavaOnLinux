sed -i 's/<int>/<Integer>/g' AlignmentCore/FittingModeArr.java
sed -i 's/for (int element : initialElements)/for (Integer element : initialElements)/g' AlignmentCore/FittingModeArr.java
sed -i 's/public int get(int index) {/public Integer get(int index) {/g' AlignmentCore/FittingModeArr.java
sed -i 's/return doGet(index);/long cptr = SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type.getCPtr(doGet(index)); return (int)cptr;/g' AlignmentCore/FittingModeArr.java
sed -i 's/public int set(int index, int e) {/public Integer set(int index, Integer e) {/g' AlignmentCore/FittingModeArr.java
sed -i 's/return doSet(index, e);/long oldCptr = SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type.getCPtr(doSet(index, new SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type(e.longValue(), false))); return (int)oldCptr;/g' AlignmentCore/FittingModeArr.java
sed -i 's/public boolean add(int e) {/public boolean add(Integer e) {/g' AlignmentCore/FittingModeArr.java
sed -i 's/doAdd(e);/doAdd(new SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type(e.longValue(), false));/g' AlignmentCore/FittingModeArr.java
sed -i 's/public void add(int index, int e) {/public void add(int index, Integer e) {/g' AlignmentCore/FittingModeArr.java
sed -i 's/doAdd(index, e);/doAdd(index, new SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type(e.longValue(), false));/g' AlignmentCore/FittingModeArr.java
sed -i 's/public int remove(int index) {/public Integer remove(int index) {/g' AlignmentCore/FittingModeArr.java
sed -i 's/return doRemove(index);/long cptr = SWIGTYPE_p_std__vectorT_AlignmentFittingCore__ALFITMODE_t__value_type.getCPtr(doRemove(index)); return (int)cptr;/g' AlignmentCore/FittingModeArr.java
