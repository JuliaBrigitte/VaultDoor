import React from 'react';
import PropTypes from 'prop-types';
import {FontAwesomeIcon} from "@fortawesome/react-fontawesome";

/**
 * presents a listbox with a filter control above it to the user.
 * intented to be used with server-side filtering; in this case set `unfilteredContentFetchUrl` to the server URL to request data from.
 * you can provide a callback in `unfilteredContentConverter` that will receive the data that came back from the URL. The control expects
 * it to return an array of {name: "displayname", value: "somevalue"} objects to populate the list.
 * if the `initialLoad` property is true then a load is attempted when the component mounts, otherwise only as the user is typing.
 *
 * example (server GET request):
 * <FilterableList unfilteredContentFetchUrl="https://my-server/api/searchendpoint"
 *                 fetchUrlFilterQuery="name"
 *                 unfilteredContentConverter={this.contentfunc}
 *                 onChange={newValue=>this.setState({thing: newValue})}
 *                 value={this.state.thing}
 *                 size={15}
 * />
 * will perform a GET to https://my-server/api/searchendpoint?name={searchboxcontent} each time the search box content is updated,
 * call `this.contentfunc` to convert the server response into name-value pairs and display them in the box. When a user select the item,
 * the container's state variable called `thing` is updated to the `value` parameter of the selected item in the box
 *
 * example (server PUT request):
 * <FilterableList unfilteredContentFetchUrl="https://my-server/api/searchendpoint"
 *                 makeSearchDoc={this.prepareContentSearch}
 *                 unfilteredContentConverter={this.contentfunc}
 *                 onChange={newValue=>this.setState({thing: newValue})}
 *                 value={this.state.thing}
 *                 size={15}
 * />
 * will perform a PUT to https://my-server/api/searchendpoint with a JSON body that is converted directly from the
 * return value of `this.prepareContentSearch`. The returned data will be passed to `this.contentFunc` which needs to decode it to
 * an array of {name: "displayname",value: "somevalue"}. If the data is already in this format then `unfilteredContentConverter` can be
 * omitted
 */
class FilterableList extends React.Component {
    static propTypes = {
        unfilteredContent: PropTypes.array,
        unfilteredContentFetchUrl: PropTypes.string,
        makeSearchDoc: PropTypes.func,
        fetchUrlFilterQuery: PropTypes.string,
        unfilteredContentConverter: PropTypes.func,
        initialLoad: PropTypes.bool,
        onChange: PropTypes.func.isRequired,
        value: PropTypes.string.isRequired,
        onFiltered: PropTypes.func,
        size: PropTypes.number.isRequired
    };

    constructor(props){
        super(props);
        this.state = {
            currentSearch: "",
            contentFromServer: []
        };

        //this.selectChanged = this.selectChanged.bind(this);
    }

    defaultContentConverter(incomingLines) {
        return incomingLines;
    }

    setStatePromise(newState){
        return new Promise((resolve, reject)=>{
            this.setState(newState,()=>resolve()).catch(err=>reject(err));
        })
    }

    componentDidMount() {
        if(this.props.initialLoad) this.fetchFromServer("");
    }

    async fetchFromServer(searchParam){
        const getUrl = this.props.unfilteredContentFetchUrl + "?" + this.props.fetchUrlFilterQuery + "=" + searchParam;

        const result = await (this.props.makeSearchDoc ? fetch(this.props.unfilteredContentFetchUrl ,{method: "PUT", body: JSON.stringify(this.props.makeSearchDoc(searchParam))}) : fetch(getUrl));
        const content = await result.json();

        try {
            const convertedContent = this.props.unfilteredContentConverter ? this.props.unfilteredContentConverter(content) : this.defaultContentConverter(content);
            return this.setStatePromise({contentFromServer: convertedContent, loading: false});
        } catch (err) {
            console.error("Could not convert content: ", err);
        }
    }

    async filterStatic(searchParam){

    }

    componentDidUpdate(prevProps, prevState, snapshot) {
        if(prevState.currentSearch!==this.state.currentSearch){
            const completionPromise = this.props.unfilteredContentFetchUrl ? this.fetchFromServer(this.state.currentSearch) : this.filterStatic(this.state.currentSearch);

            completionPromise.then(()=> {
                if (this.props.onFiltered) this.props.onFiltered(this.state.currentSearch);
            }).catch(err=>{
                this.setState({loading: false, lastError: err})
            })
        }
    }

    render(){
        const listContent = this.props.unfilteredContent ? this.props.unfilteredContent : this.state.contentFromServer;

        return <div>
            <ul className="no-decorations">
                <li>
                    <FontAwesomeIcon icon="search"/>
                    <input onChange={evt=>this.setState({currentSearch: evt.target.value})} value={this.state.currentSearch}/>
                </li>
                <li>
                    <select className="filterable-list-selector" size={this.props.size} onChange={evt=>this.props.onChange(evt.target.value)}>
                        {
                            listContent.map((entry,ix)=>{
                                return <option key={ix} value={entry.value}>{entry.name}</option>
                            })
                        }
                    </select>
                </li>
            </ul>
        </div>
    }
}

export default FilterableList;